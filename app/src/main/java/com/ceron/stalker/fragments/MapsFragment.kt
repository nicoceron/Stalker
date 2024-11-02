package com.ceron.stalker.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Location
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import com.ceron.stalker.R
import com.ceron.stalker.activities.MainActivity
import com.ceron.stalker.databinding.FragmentMapsBinding
import com.ceron.stalker.models.UserProfile
import com.ceron.stalker.utils.Alerts
import com.ceron.stalker.utils.GeocoderSearch
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference


class MapsFragment : Fragment(), SensorEventListener {

    private lateinit var binding: FragmentMapsBinding
    private lateinit var alerts: Alerts
    private lateinit var geocoderSearch: GeocoderSearch
    private lateinit var gMap: GoogleMap
    private lateinit var sensorManager: SensorManager
    private lateinit var lightSensor: Sensor
    private var zoomLevel = 15f
    private var moveCamera = true
    private lateinit var userMarker: Marker
    private lateinit var position: LatLng
    val colombia = LatLng(4.714, -74.03)
    private var routePolyline: Polyline? = null
    private val routePoints: MutableList<LatLng> = mutableListOf()

    private val storageReference = FirebaseStorage.getInstance().reference

    private val otherUserMarkers = mutableMapOf<String, Marker>()
    private lateinit var database: DatabaseReference
    private var valueEventListener: ValueEventListener? = null

    private var pendingLocation: Location? = null



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentMapsBinding.inflate(inflater, container, false)

        //Map setup
        val callback = OnMapReadyCallback { googleMap ->
            gMap = googleMap
            gMap.uiSettings.isZoomControlsEnabled = false
            gMap.uiSettings.isCompassEnabled = true

            userMarker = gMap.addMarker(
                MarkerOptions().position(colombia).title("")
                    .icon(context?.let {
                        bitmapDescriptorFromVector(
                            it,
                            R.drawable.baseline_person_pin_circle_24
                        )
                    })
            )!!

            pendingLocation?.let {
                moveUser(it)
                pendingLocation = null
            }

            gMap.moveCamera(CameraUpdateFactory.newLatLng(colombia))

            gMap.setOnMapLongClickListener { latLng -> addPoint(latLng) }
            routePolyline = gMap.addPolyline(PolylineOptions().width(5f).color(Color.RED))
        }
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)

        binding.switchFollowUser.isChecked = false
        database = FirebaseDatabase.getInstance().reference.child("users")
        binding.switchFollowUser.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                (activity as? MainActivity)?.toggleLocationSharing(true)
                startListeningToOtherUsers()
            } else {
                (activity as? MainActivity)?.toggleLocationSharing(false)
                stopListeningToOtherUsers()
                clearOtherUserMarkers()
            }
        }

        //Address search
        alerts = Alerts(this.requireContext())
        geocoderSearch = GeocoderSearch(this.requireContext())


//        // searching stuff
//        binding.searchField.editText?.setOnEditorActionListener { _, actionId, _ ->
//            return@setOnEditorActionListener when (actionId) {
//                EditorInfo.IME_ACTION_SEARCH -> {
//                    val text = binding.searchField.editText?.text.toString()
//                    val address: MutableList<Address>? = geocoderSearch.finPlacesByNameInRadius(
//                        text,
//                        LatLng(position.latitude, position.longitude)
//                    )
//
//                    if (address.isNullOrEmpty()) {
//                        alerts.shortToast("No se encontraron resultados")
//                        false
//                    } else {
//                        alerts.shortToast("Se encontraron ${address.size} resultados")
//
//                        // Get the first result to move the camera to its location
//                        val firstResult = address[0]
//                        val firstLatLng = LatLng(firstResult.latitude, firstResult.longitude)
//
//                        // Move the camera to the first result
//                        gMap.animateCamera(
//                            CameraUpdateFactory.newLatLngZoom(
//                                firstLatLng,
//                                zoomLevel
//                            )
//                        )
//
//                        // Add markers for each result using the same logic as in addPoint()
//                        address.forEach {
//                            val latLng = LatLng(it.latitude, it.longitude)
//                            addPoint(latLng)
//                        }
//
//                        true
//                    }
//                }
//
//                else -> false
//            }
//        }

        //Sensor
        sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!



        return binding.root
    }

    private fun startListeningToOtherUsers() {
        database = FirebaseDatabase.getInstance().reference.child("users")
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key
                    val userProfile = userSnapshot.getValue(UserProfile::class.java)

                    if (userId != (activity as? MainActivity)?.currentUser?.uid && userProfile != null) {
                        if (userProfile.online) {
                            val latLng = LatLng(userProfile.latitude ?: 0.0, userProfile.longitude ?: 0.0)

                            val profileImageRef = storageReference.child("users/$userId/profile.jpg")

                            // Glide para cargar la imagen de perfil
                            Glide.with(this@MapsFragment)
                                .asBitmap()
                                .load(profileImageRef)
                                .circleCrop()
                                .override(75, 75)
                                .into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        val markerIcon = BitmapDescriptorFactory.fromBitmap(resource)

                                        if (otherUserMarkers.containsKey(userId)) {
                                            otherUserMarkers[userId]?.apply {
                                                position = latLng
                                                setIcon(markerIcon)
                                            }
                                        } else {
                                            val marker = gMap.addMarker(
                                                MarkerOptions()
                                                    .position(latLng)
                                                    .title(userProfile.name)
                                                    .icon(markerIcon)
                                            )
                                            otherUserMarkers[userId!!] = marker!!
                                        }
                                    }

                                    override fun onLoadCleared(placeholder: Drawable?) {
                                        // Manejar la limpieza si es necesario
                                    }
                                })
                        } else {
                            // Remover marcador si el usuario está offline
                            otherUserMarkers[userId]?.remove()
                            otherUserMarkers.remove(userId)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejar error
            }
        }

        database.limitToFirst(100).addValueEventListener(valueEventListener!!)
    }

    private fun stopListeningToOtherUsers() {
        valueEventListener?.let {
            database.removeEventListener(it)
            valueEventListener = null
        }
    }

    private fun clearOtherUserMarkers() {
        for (marker in otherUserMarkers.values) {
            marker.remove()
        }
        otherUserMarkers.clear()
    }



    //From https://stackoverflow.com/questions/42365658/custom-marker-in-google-maps-in-android-with-vector-asset-icon
    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap =
                Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    fun moveUser(location: Location) {
        if (this::userMarker.isInitialized) {
            val newPos = LatLng(location.latitude, location.longitude)
            position = newPos
            userMarker.position = newPos

            // Mover la cámara si es necesario
//            if (moveCamera) {
//                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPos, zoomLevel))
//            }
        } else {
            // Almacenar la ubicación hasta que el marcador esté listo
            pendingLocation = location
        }
    }


    private fun addPoint(latLng: LatLng) {
        val address = geocoderSearch.findAddressByPosition(latLng)
        val addressText = address?.getAddressLine(0) ?: "Dirección no encontrada"
        val snippetText =
            "Dirección: $addressText\nLat: ${latLng.latitude}, Lng: ${latLng.longitude}"

        val markerOptions = MarkerOptions()
            .position(latLng)
            .title("Ubicación Seleccionada")
            .snippet(snippetText)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

        gMap.addMarker(markerOptions)?.showInfoWindow()
    }


//    private fun addStore(location: LatLng, title: String, desc: String) {
//        gMap.addMarker(
//            MarkerOptions().position(location).title(title).snippet(desc).icon(
//                context?.let { bitmapDescriptorFromVector(it, R.drawable.direction) })
//        )
//    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (this::gMap.isInitialized) {
            if (event!!.values[0] > 80) {
                gMap.setMapStyle(
                    context?.let { MapStyleOptions.loadRawResourceStyle(it, R.raw.map_day) })
            } else {
                gMap.setMapStyle(
                    context?.let { MapStyleOptions.loadRawResourceStyle(it, R.raw.map_night) })
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        //Do nothing
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListeningToOtherUsers()
        clearOtherUserMarkers()
    }

}