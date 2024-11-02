package com.ceron.stalker.activities

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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.storage

class MainActivity : AuthorizedActivity() {

    private val TAG = MainActivity::class.java.name
    private lateinit var binding: ActivityMainBinding
    private val PERM_LOCATION_CODE = 303
    private lateinit var position: Location
    private lateinit var fragment: MapsFragment

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var isTracking = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupLocation()
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }


            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERM_LOCATION_CODE
                )
            }
        }
        //Setup fragment
        fragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as MapsFragment

        //Set TopBar events
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.user_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }

                R.id.user_logout -> {
                    logout()
                    true
                }

                else -> false
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_LOCATION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                } else {
                    alerts.shortSimpleSnackbar(
                        binding.root,
                        "My location permissions jut got denied 😭"
                    )
                }
            }
        }
    }

    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateDistanceMeters(5F)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    position = location
                    fragment.moveUser(location)
                    if (isTracking) {
                        updateUserLocation(location)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        } else {
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    fun toggleLocationSharing(isSharing: Boolean) {
        isTracking = isSharing
        if (isTracking) {
            startLocationUpdates()
            refData.child("online").setValue(true)
        } else {
            stopLocationUpdates()
            refData.child("online").setValue(false)
        }
    }

    private fun updateUserLocation(location: Location) {
        val updates = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude
        )
        refData.updateChildren(updates)
    }

    override fun onStart() {
        super.onStart()
        if (isTracking) {
            refData.child("online").setValue(true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isTracking) {
            refData.child("online").setValue(false)
        }
    }

    fun isTracking(): Boolean {
        return isTracking
    }
}