package com.piled.winnie;

import java.io.File;
import java.util.List;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.util.Log;
import android.widget.Toast;

import android.content.Context;
import android.location.Location;
import android.widget.ProgressBar;

import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import android.support.v4.widget.SwipeRefreshLayout;

public class MainActivity extends Activity implements ItemLoader.itemReady,
    ConnectionCallbacks, OnConnectionFailedListener, LocationListener,
    GoogleMap.OnMapClickListener, OnMapReadyCallback {

    private static final String TAG = "winnie::map";
    private GoogleApiClient mGoogleApiClient;
    private GoogleMap mMap;
    private static final LocationRequest REQUEST = LocationRequest.create()
        .setInterval(5000)         // 5 seconds
        .setFastestInterval(16)    // 16ms = 60fps
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    
    private ProgressBar mWorkProgress;
    private SwipeRefreshLayout mSwipeContainer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.i(TAG, "onCreate()");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this).build();
        final MapFragment mapFragment = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mMap = mapFragment.getMap();
        if (mMap == null) {
            Log.i(TAG, "onCreate(): waiting for map");
            mapFragment.getMapAsync(this);
        } else {
            Log.i(TAG, "onCreate(): map is ready");
            onMapReady(mMap);
        }
        mWorkProgress = (ProgressBar)findViewById(R.id.work_progress);
        mSwipeContainer = (SwipeRefreshLayout)findViewById(R.id.swipeContainer);
        mSwipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mMap != null) {
                    LatLng ll = mMap.getCameraPosition().target;
                    new ProducerTask(ll.latitude, ll.longitude).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                }
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        ItemLoader.setCallback(this);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
        ItemLoader.setCallback(null);
    }
    
    @Override
    public void onMapClick(LatLng point) {
    //    String msg = "Location = " + point;
    //    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        map.setMyLocationEnabled(true);
        mMap = map;
        //mMap.setOnMapClickListener(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "new location is " + location);
        Location loc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LatLng ll = new LatLng(loc.getLatitude(),loc.getLongitude());
        new ProducerTask(loc.getLatitude(),loc.getLongitude()).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 14)); // 16
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, REQUEST, this);  // LocationListener
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }
    
    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }
    
    @Override
    public void onReady(final Item item) {
        Log.d(TAG, "onReady " + item.name);
        if (mMap != null && item != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Log.d(TAG, "onReady:run() " + item.name);
                    MarkerOptions options = new MarkerOptions().position(new LatLng(item.latitude, item.longitude)).title(item.name);
                    int lastSlash = item.icon.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        String filename = ItemLoader.THUMBNAIL_PATH + item.icon.substring(lastSlash);
                        File file = new File(filename);
                        if (file.exists()) {
                            options = options.icon(BitmapDescriptorFactory.fromPath(filename));
                        }
                    }
                    mMap.addMarker(options);
                }
            });
        }
    }

    // TODO should remove "outside of map" markers
    class ProducerTask extends AsyncTask<Void, Void, List<Item>> {
        double lat;
        double lng;
        public ProducerTask(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
            mWorkProgress.setIndeterminate(true);
            mWorkProgress.setVisibility(View.VISIBLE);
        }
        
        @Override
        protected List<Item> doInBackground(Void... progresses) {
            return new FoursquareProducer().requestItems(lat, lng);
        }
        
        @Override
        protected void onPostExecute(List<Item> items) {
            mWorkProgress.setVisibility(View.GONE);
            mSwipeContainer.setRefreshing(false);
        }

    }
}
