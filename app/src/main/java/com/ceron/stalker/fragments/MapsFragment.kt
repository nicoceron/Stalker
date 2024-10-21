package com.ceron.stalker.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.ceron.stalker.databinding.FragmentMapsBinding
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
            gMap.moveCamera(CameraUpdateFactory.newLatLng(colombia))

            gMap.setOnMapLongClickListener { latLng -> addPoint(latLng) }
            routePolyline = gMap.addPolyline(PolylineOptions().width(5f).color(Color.RED))
        }
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)

        binding.switchFollowUser.setOnCheckedChangeListener { _, isChecked ->
            moveCamera = isChecked
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
        val newPos = LatLng(location.latitude, location.longitude)
        position = newPos
        userMarker.position = newPos

        // Add position to route
        routePoints.add(newPos)
        routePolyline?.points = routePoints

        if (moveCamera) {
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPos, zoomLevel))
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
}