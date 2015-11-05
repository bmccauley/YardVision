package com.princeton.bmccauley.yardvision;

import android.location.Location;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;

public class CameraTask extends AsyncTask<Void, Void, Void> {

    protected Location mLastLocation;
    protected Location mCurrentLocation;
    protected CameraActivity cameraActivity;
    //e-mail address to monitor app status
    protected String[] emailTo = {"bmccauley@princeton.com"};
    protected String emailFrom = "princetonyardvision@gmail.com";
    protected String password = "YardVision2015";
    protected String emailSubject = "YardVision";
    protected ArrayList<Mail> queuedMailList = new ArrayList<>();
    //debug flag: set to true to take pictures even if no motion is detected
    protected boolean ignoreGPS = true;

    public void setCameraActivity(CameraActivity cameraActivity) {
        this.cameraActivity = cameraActivity;
    }

    private Mail generateEmail() {
        Mail m = new Mail(emailFrom, password);
        m.set_to(emailTo);
        m.set_from(emailFrom);
        m.set_subject(emailSubject);
        return m;
    }

    private void sendStartEmail(Date startTime) {
        Mail m = generateEmail();
        m.set_body("Yard Vision app started at " + startTime);
        try {
            m.send();
        } catch (Exception e) {
            e.printStackTrace();
            queuedMailList.add(m);
        }
    }

    private void sendStopEmail() {
        Mail m = generateEmail();
        Date stopTime = new Date();
        m.set_body("Yard Vision app stopped at " + stopTime);
        try {
            m.send();
        } catch (Exception e) {
            e.printStackTrace();
            queuedMailList.add(m);
        }
    }

    private void sendCrashEmail(Exception exception) {
        Mail m = generateEmail();
        Date crashTime = new Date();
        m.set_body("Yard Vision app crashed at " + crashTime + " with exception " + exception);
        try {
            m.send();
        } catch (Exception e) {
            e.printStackTrace();
            queuedMailList.add(m);
        }
    }

    private void sendPhotoEmail() {
        Mail m = generateEmail();
        try {
            m.addAttachment(cameraActivity.filePath);
            m.send();
        } catch (Exception e) {
            e.printStackTrace();
            queuedMailList.add(m);
        }
    }

    private void sendQueuedMail() {
        ArrayList<Mail> sentMailList = new ArrayList<>();
        for (Mail m : queuedMailList) {
            try {
                m.send();
                sentMailList.add(m);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        queuedMailList.removeAll(sentMailList);
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            Date startTime = new Date();
            Date currentTime;
            Date emailPhotoTime = startTime;
            sendStartEmail(startTime);
            //max 24 hour run time
            long maxRunTime = 24 * 60 * 60 * 1000;
            do {
                mCurrentLocation = cameraActivity.mCurrentLocation;
                if (mLastLocation == null || ignoreGPS) {
                    cameraActivity.mCamera.startPreview();
                    cameraActivity.mCamera.takePicture(null, null, cameraActivity.getPictureCallback());
                    mLastLocation = mCurrentLocation;
                } else if (mCurrentLocation != null && mCurrentLocation.distanceTo(mLastLocation) > 2) {
                    //Take a picture if the camera has moved more than 2 meters since the last picture
                    cameraActivity.mCamera.startPreview();
                    cameraActivity.mCamera.takePicture(null, null, cameraActivity.getPictureCallback());
                    mLastLocation = mCurrentLocation;
                }

                //Every 10 minutes, send the last photo taken
                currentTime = new Date();
                if (currentTime.getTime() - emailPhotoTime.getTime() > 10 * 60 * 1000) {
                    sendPhotoEmail();
                    emailPhotoTime = currentTime;
                    sendQueuedMail();
                }

                try {
                    //1.5 seconds is near the minimum cycle time; 1 second or less will crash the app
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }
                if (Thread.currentThread().isInterrupted()) break;
        } while (currentTime.getTime() - startTime.getTime() < maxRunTime);
            cameraActivity.releaseCameraAndPreview();
        } catch (Exception e) {
            e.printStackTrace();
            //File path is hardware-dependent; this points to the SD card on Samsung devices
            File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getParent() + "/extSdCard", "YardVision");
            String filePath = mediaStorageDir.getPath() + File.separator + "error.txt";
            File error = new File(filePath);
            try {
                FileOutputStream fos = new FileOutputStream(error);
                fos.write(e.toString().getBytes());
                fos.close();
            } catch (Exception exc) {}
            sendCrashEmail(e);
            return null;
        }
        sendStopEmail();
        return null;
    }
}
