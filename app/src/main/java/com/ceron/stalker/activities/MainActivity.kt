package com.ceron.stalker.activities

// Imports required for location services, UI management, Firebase, and other utilities
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ceron.stalker.R
import com.ceron.stalker.databinding.ActivityMainBinding
import com.ceron.stalker.fragments.MapsFragment
import com.ceron.stalker.utils.Alerts
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.StorageReference

// MainActivity responsible for handling location permissions, location tracking, and user interactions with the UI
class MainActivity : AuthorizedActivity() {

    private val TAG = MainActivity::class.java.name
    private lateinit var binding: ActivityMainBinding
    private val PERM_LOCATION_CODE = 303 // Permission code for location
    private lateinit var position: Location // Tracks the user's current position
    private lateinit var fragment: MapsFragment

    private lateinit var fusedLocationClient: FusedLocationProviderClient // Client for location updates
    private lateinit var locationRequest: LocationRequest // Specifies parameters for location updates
    private lateinit var locationCallback: LocationCallback // Callback to handle location updates

    private var isTracking = false // Tracks if location sharing is enabled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge layout for immersive UI
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply padding based on system bar insets for UI alignment
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize location services and set up permissions
        setupLocation()
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates() // Start updates if permission is granted
            }

            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERM_LOCATION_CODE
                ) // Request permission if not granted
            }
        }

        // Initialize map fragment
        fragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as MapsFragment

        // Set up event listeners for top app bar actions
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.user_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }

                R.id.user_logout -> {
                    logout() // Handle user logout
                    true
                }

                else -> false
            }
        }
        setupOnDisconnect()
    }

    private fun setupOnDisconnect() {
        refData.child("online").onDisconnect().setValue(false)
    }

    // Handle permission request result for location access
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_LOCATION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates() // Start updates if permission granted
                } else {
                    alerts.shortSimpleSnackbar(
                        binding.root,
                        "Location permissions were denied 😭"
                    ) // Notify user if permission denied
                }
            }
        }
    }

    // Configures location request parameters and defines the callback for receiving location updates
    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define location request parameters for high accuracy and regular updates
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateDistanceMeters(5F)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        // Callback to handle location updates and pass data to the map fragment
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    position = location
                    fragment.moveUser(location) // Update user location on map
                    if (isTracking) {
                        updateUserLocation(location) // Share location if tracking is enabled
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates() // Restart location updates when the activity resumes
        if (isTracking) {
            refData.child("online").setValue(true) // Update online status in Firebase
        }
    }

    // Starts location updates if permission is granted
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        }
    }

    // Stops location updates to preserve battery
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // Toggles location sharing and updates the Firebase database
    fun toggleLocationSharing(isSharing: Boolean) {
        isTracking = isSharing
        if (isTracking) {
            startLocationUpdates() // Start updates if sharing is enabled
            refData.child("online").setValue(true) // Update online status in Firebase
        } else {
            stopLocationUpdates() // Stop updates if sharing is disabled
            refData.child("online").setValue(false) // Update online status in Firebase
        }
    }

    // Updates user location in the Firebase database
    private fun updateUserLocation(location: Location) {
        val updates = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude
        )
        refData.updateChildren(updates) // Push location data to Firebase
    }

    // Sets user online status in Firebase when activity starts
    override fun onStart() {
        super.onStart()
        if (isTracking) {
            refData.child("online").setValue(true)
        }
    }

    // Sets user offline status in Firebase when activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        if (isTracking) {
            refData.child("online").setValue(false)
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates() // Stop location updates when the activity pauses
    }

    // Returns the current tracking state
    fun isTracking(): Boolean {
        return isTracking
    }

}
