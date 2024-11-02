package com.ceron.stalker.fragments

// Imports required for Android functionality, Google Maps, Firebase, and third-party libraries like Glide
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
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

// Fragment class that implements a map view with location tracking and other user interactions
class MapsFragment : Fragment(), SensorEventListener {

    // Properties for UI binding, Firebase references, and map settings
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
    private val colombia = LatLng(4.714, -74.03)
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
        // Inflate layout and initialize map fragment
        binding = FragmentMapsBinding.inflate(inflater, container, false)

        // Map setup, adding a marker for the user's location and configuring UI settings
        val callback = OnMapReadyCallback { googleMap ->
            gMap = googleMap
            gMap.uiSettings.isZoomControlsEnabled = false
            gMap.uiSettings.isCompassEnabled = true

            // Initialize the user marker and set the default position
            userMarker = gMap.addMarker(
                MarkerOptions().position(colombia).title("")
                    .icon(context?.let {
                        bitmapDescriptorFromVector(
                            it,
                            R.drawable.baseline_person_pin_circle_24
                        )
                    })
            )!!

            // If there's a pending location, move the user marker to it
            pendingLocation?.let {
                moveUser(it)
                pendingLocation = null
            }

            // Move the camera to the default location
            gMap.moveCamera(CameraUpdateFactory.newLatLng(colombia))

            // Add listeners for map interactions
            gMap.setOnMapLongClickListener { latLng -> addPoint(latLng) }
            routePolyline = gMap.addPolyline(PolylineOptions().width(5f).color(Color.RED))
        }

        // Get the map fragment instance and set it up
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)

        // Initialize database and toggle location sharing functionality
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

        // Setup alert system and geocoding for location searches
        alerts = Alerts(this.requireContext())
        geocoderSearch = GeocoderSearch(this.requireContext())

        // Sensor setup to adjust map style based on light sensor input
        sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

        return binding.root
    }

    // Function to start listening for other users' location updates in the Firebase database
    private fun startListeningToOtherUsers() {
        database = FirebaseDatabase.getInstance().reference.child("users")
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key
                    val userProfile = userSnapshot.getValue(UserProfile::class.java)

                    // If user is online, update their location marker on the map
                    if (userId != (activity as? MainActivity)?.currentUser?.uid && userProfile != null) {
                        if (userProfile.online) {
                            val latLng = LatLng(userProfile.latitude ?: 0.0, userProfile.longitude ?: 0.0)
                            val profileImageRef = storageReference.child("users/$userId/profile.jpg")

                            // Load and display profile image as a marker using Glide
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
                                        // Handle cleanup if needed
                                    }
                                })
                        } else {
                            // Remove marker if the user goes offline
                            otherUserMarkers[userId]?.remove()
                            otherUserMarkers.remove(userId)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error if needed
            }
        }

        // Limit database listener to the first 100 entries for efficiency
        database.limitToFirst(100).addValueEventListener(valueEventListener!!)
    }

    // Stop listening to other users' location updates
    private fun stopListeningToOtherUsers() {
        valueEventListener?.let {
            database.removeEventListener(it)
            valueEventListener = null
        }
    }

    // Remove markers representing other users from the map
    private fun clearOtherUserMarkers() {
        for (marker in otherUserMarkers.values) {
            marker.remove()
        }
        otherUserMarkers.clear()
    }

    // Convert vector drawable to BitmapDescriptor for use as a custom map marker icon
    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    // Update the user's location on the map
    fun moveUser(location: Location) {
        if (this::userMarker.isInitialized) {
            val newPos = LatLng(location.latitude, location.longitude)
            position = newPos
            userMarker.position = newPos

            // Optionally move the camera to follow the user
//            if (moveCamera) {
//                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPos, zoomLevel))
//            }

        } else {
            // Store location if marker isn't ready yet
            pendingLocation = location
        }
    }

    // Add a marker on the map at the specified LatLng position with address information
    private fun addPoint(latLng: LatLng) {
        val address = geocoderSearch.findAddressByPosition(latLng)
        val addressText = address?.getAddressLine(0) ?: "Address not found"
        val snippetText = "Address: $addressText\nLat: ${latLng.latitude}, Lng: ${latLng.longitude}"

        val markerOptions = MarkerOptions()
            .position(latLng)
            .title("Selected Location")
            .snippet(snippetText)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

        gMap.addMarker(markerOptions)?.showInfoWindow()
    }

    // Register the light sensor listener when fragment resumes
    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    // Unregister the light sensor listener when fragment pauses
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Adjust map style based on light sensor readings
    override fun onSensorChanged(event: SensorEvent?) {
        if (this::gMap.isInitialized) {
            if (event!!.values[0] > 80) {
                gMap.setMapStyle(context?.let { MapStyleOptions.loadRawResourceStyle(it, R.raw.map_day) })
            } else {
                gMap.setMapStyle(context?.let { MapStyleOptions.loadRawResourceStyle(it, R.raw.map_night) })
            }
        }
    }

    // No action needed for accuracy changes
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Clean up listeners and markers when view is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        stopListeningToOtherUsers()
        clearOtherUserMarkers()
    }
}
