package com.timhagn.rngloc;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Created by benjakuben on 12/17/14.
 */
public class LocationProvider implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    /**
     * Location Callback interface to be defined in Module
     */
    public abstract interface LocationCallback {
        public abstract void handleNewLocation(Location location);
    }

    // Unique Name for Log TAG
    public static final String TAG = LocationProvider.class.getSimpleName();
    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    // Location Callback for later use
    private LocationCallback mLocationCallback;
    // Context for later use
    private Context mContext;
    // Main Google API CLient (Google Play Services API)
    private GoogleApiClient mGoogleApiClient;
    // Location Request for later use
    private LocationRequest mLocationRequest;
    // Are we Connected?
    public Boolean connected;
    // Do we have play services?
    private Boolean hasPlayServices;
    private Boolean isInited;
    private LocationManager locationManager;

    public LocationProvider(Context context, LocationCallback updateCallback) {
        // Save current Context
        mContext = context;
        // Save Location Callback
        this.mLocationCallback = updateCallback;
        // Initialize connection "state"
        connected = false;
        this.isInited = false;

        // First we need to check availability of play services
        if (checkPlayServices()) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();

            // Create the LocationRequest object
            mLocationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                    .setFastestInterval(1000);     // 1 second, in milliseconds

            hasPlayServices = true;
        } else {

            hasPlayServices = false;

        }
    }

    /**
     * Method to verify google play services on the device
     */
    public boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(mContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                Log.i(TAG, GooglePlayServicesUtil.getErrorString(resultCode));
            } else {
                Log.i(TAG, "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    /**
     * Connects to Google Play Services - Location
     */
    public void connect() {

        if (hasPlayServices) {
            mGoogleApiClient.connect();
        } else {
            Log.i(TAG, "This device does not support google play services.");
        }
    }

    /**
     * Disconnects to Google Play Services - Location
     */
    public void disconnect() {

        if (hasPlayServices) {

            if (mGoogleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }

        } else {
            Log.i(TAG, "This device does not support google play services.");
        }
    }

    public boolean isLocationAvailable() {

        if (hasPlayServices) {
            return LocationServices.FusedLocationApi.getLocationAvailability(mGoogleApiClient).isLocationAvailable();
        } else {
            return false;
        }
    }

    public void reinitIfNeeded() {
        Location location = null;
        Log.i(TAG, "Location services reinit.");
        if (this.isInited) return;

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        try {
            location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (location != null) {
                mLocationCallback.handleNewLocation(location);
            }
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            this.isInited = true;
        } catch (Exception e) {
            this.isInited = false;
            Log.i(TAG, "Location services reinit ERROR.");
            e.printStackTrace();
        }

        if (location == null) {
            Location mLastLocation = null;
            LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            mLastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (mLastLocation != null) {
                mLocationCallback.handleNewLocation(mLastLocation);
            } else {
                mLastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (mLastLocation != null) {
                    mLocationCallback.handleNewLocation(mLastLocation);
                }
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connected.");
        // We are Connected!
        connected = true;
        try {
            // First, get Last Location and return it to Callback
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (location != null) {
                mLocationCallback.handleNewLocation(location);
            }
            // Now request continuous Location Updates
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            this.isInited = true;
        } catch (Exception e) {
            this.isInited = false;
            Log.i(TAG, "Location services connected ERROR.");
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended...");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution() && mContext instanceof Activity) {
            try {
                Activity activity = (Activity) mContext;
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(activity, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            /*
             * Thrown if Google Play services canceled the original
             * PendingIntent
             */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        //Log.i(TAG, "Location Changed!");
        // Callback as defined in Module.
        mLocationCallback.handleNewLocation(location);
    }
}
