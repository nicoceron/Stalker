package com.ceron.stalker.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

class GeocoderSearch (context: Context){

    private var TAG = GeocoderSearch::class.java.name
    private val AVERAGE_RADIUS_OF_EARTH_KM = 6371.0
    private val MAX_RESULTS = 20
    private val DISTANCE_RADIUS_KM = 20.0
    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())

    fun findPlacesByName(name: String): MutableList<Address>? {
        return geocoder.getFromLocationName(name, MAX_RESULTS)
    }

    fun findAddressByPosition(position: LatLng): Address? {
        return geocoder.getFromLocation(position.latitude, position.longitude, 1)?.first()
    }

    fun finPlacesByNameInRadius(name: String, centerPosition: LatLng): MutableList<Address>? {
        val upperLeftPosition: LatLng = this.moveLatLngInKilometer(
            -DISTANCE_RADIUS_KM,
            -DISTANCE_RADIUS_KM,
            centerPosition
        )
        val bottomRightPosition: LatLng = this.moveLatLngInKilometer(
            DISTANCE_RADIUS_KM,
            DISTANCE_RADIUS_KM,
            centerPosition
        )
        return geocoder.getFromLocationName(
            name,
            MAX_RESULTS,
            upperLeftPosition.latitude,
            upperLeftPosition.longitude,
            bottomRightPosition.latitude,
            bottomRightPosition.longitude
        );
    }

    private fun moveLatLngInKilometer(latMove: Double, lngMove: Double, position: LatLng): LatLng {
        val newLatitude: Double =
            position.latitude + latMove / AVERAGE_RADIUS_OF_EARTH_KM * (180 / Math.PI)
        val newLongitude: Double =
            position.longitude + lngMove / AVERAGE_RADIUS_OF_EARTH_KM * (180 / Math.PI) / Math.cos(
                position.latitude * Math.PI / 180
            )
        return LatLng(newLatitude, newLongitude)
    }
}