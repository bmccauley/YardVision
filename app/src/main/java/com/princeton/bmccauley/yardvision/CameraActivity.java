package com.princeton.bmccauley.yardvision;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CameraActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    protected GoogleApiClient mGoogleApiClient;
    protected static final String TAG = "yard-vision";
    CameraTask cameraTask;
    Camera mCamera;
    CameraPreview mPreview;
    Location mCurrentLocation;
    String filePath;

    public void stop(View view) {
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cameraTask.cancel(true);
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    protected void onStart() {
        super.onStart();
        buildGoogleApiClient();
        mGoogleApiClient.connect();
        cameraTask = new CameraTask();
        cameraTask.setCameraActivity(this);
        mPreview = new CameraPreview(this, null);
        safeCameraOpen(0);
        mPreview.setCamera(mCamera);
        //All parameters are hardware-dependent; these settings are for a Samsung Galaxy camera.
        Camera.Parameters params = mCamera.getParameters();
        String paramString = params.flatten();
        //params.set("mode", "m");
        //Aperture and shutter speed settings may be adjusted to correct motion blur.
        //However, this seems to break automatic white balancing, which is required for outdoor use.
        //params.set("aperture", "80"); //can be 28 (light) 32 35 40 45 50 56 63 71 80 (dark) on default zoom
        //params.set("shutter-speed", 46); //Maximum shutter speed; will crash if set any higher.
        //params.setExposureCompensation(6);
        //params.set("iso", 1600);
        //OCR works best on grayscale images.
        params.setColorEffect(Camera.Parameters.EFFECT_MONO);
        //params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        //params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
        //params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_DAYLIGHT);
        String exposures = params.get("exposure-values");
        String wb = params.get("whitebalance-values");
        //Preview and picture resolutions must match; 1080p is the minimum for legibility.
        //Supported resolutions may be found in the paramString.
        params.setPreviewSize(1920, 1080);
        params.setPictureSize(1920, 1080);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        mCamera.setParameters(params);
        cameraTask.execute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment()).commit();
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, createLocationRequest(), this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.activity_camera,
                    container, false);
        }
    }

    private void safeCameraOpen(int id) {
        releaseCameraAndPreview();
        mCamera = Camera.open(id);
    }

    public void releaseCameraAndPreview() {
        mPreview.setCamera(null);
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    public Camera.PictureCallback getPictureCallback() {
        return new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {

                File pictureFile = getOutputMediaFile();

                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(pictureFile);
                    mediaScanIntent.setData(contentUri);
                    sendBroadcast(mediaScanIntent);
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
        };
    }

    /**
     * Create a File for saving an image or video
     */
    private File getOutputMediaFile() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        //File path is hardware-dependent; this points to the SD card on Samsung devices
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getParent() + "/extSdCard", "YardVision");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("YardVision", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = SimpleDateFormat.getDateTimeInstance().format(new Date()).replace(" ", "").replace(":", ".");
        filePath = mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg";
        return new File(filePath);
    }

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        //update interval in milliseconds
        mLocationRequest.setInterval(400);
        mLocationRequest.setFastestInterval(400);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
    }
}
