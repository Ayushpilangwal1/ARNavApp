package com.arnavapp;

import android.app.Activity;
import android.util.Log;
import android.view.Surface;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import javax.microedition.khronos.opengles.GL10;

public class ARCoreManager {

    private static final String TAG = "ARCoreManager";

    private Session session;
    private Frame currentFrame;
    private boolean sessionInitialized = false;
    private boolean installRequested = false;

    private int cameraTextureId = -1;
    private int displayWidth = -1;
    private int displayHeight = -1;

    public interface ARCoreListener {
        void onTrackingStateChanged(TrackingState state);
        void onSessionError(String errorMessage);
    }

    private final ARCoreListener listener;

    public ARCoreManager(ARCoreListener listener) {
        this.listener = listener;
    }

    public boolean initSession(Activity activity) {
        if (sessionInitialized) return true;

        try {
            ArCoreApk.InstallStatus status = ArCoreApk.getInstance().requestInstall(activity, !installRequested);
            if (status == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true;
                return false;
            }

            session = new Session(activity);
            Config config = new Config(session);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            session.configure(config);

            sessionInitialized = true;

            if (cameraTextureId != -1) {
                session.setCameraTextureName(cameraTextureId);
            }
            if (displayWidth != -1 && displayHeight != -1) {
                session.setDisplayGeometry(Surface.ROTATION_0, displayWidth, displayHeight);
            }

            return true;

        } catch (UnavailableArcoreNotInstalledException e) {
            notifyError("ARCore not installed. Please install Google Play Services for AR.");
        } catch (UnavailableApkTooOldException e) {
            notifyError("ARCore APK too old. Please update Google Play Services for AR.");
        } catch (UnavailableSdkTooOldException e) {
            notifyError("App SDK too old for ARCore.");
        } catch (UnavailableDeviceNotCompatibleException e) {
            notifyError("This device does not support ARCore.");
        } catch (Exception e) {
            notifyError("ARCore session creation failed: " + e.getMessage());
        }
        return false;
    }

    public void setCameraTextureName(int textureId) {
        cameraTextureId = textureId;
        if (session != null) {
            session.setCameraTextureName(textureId);
        }
    }

    public void setDisplayGeometry(GL10 gl, int width, int height) {
        displayWidth = width;
        displayHeight = height;
        if (session != null) {
            session.setDisplayGeometry(Surface.ROTATION_0, width, height);
        }
    }

    public void update() {
        if (session == null || !sessionInitialized) return;
        try {
            currentFrame = session.update();
            if (listener != null && currentFrame != null) {
                listener.onTrackingStateChanged(currentFrame.getCamera().getTrackingState());
            }
        } catch (CameraNotAvailableException e) {
            Log.w(TAG, "Camera not available: " + e.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Frame update error: " + e.getMessage());
        }
    }

    /** Returns the full Pose (Position + Rotation) of the camera */
    public Pose getCurrentPose() {
        if (currentFrame == null) return null;
        Camera camera = currentFrame.getCamera();
        if (camera.getTrackingState() != TrackingState.TRACKING) return null;
        return camera.getPose();
    }

    public Frame getCurrentFrame() {
        return currentFrame;
    }

    /** Returns current x,y,z in metres */
    public float[] getCurrentPosition() {
        Pose pose = getCurrentPose();
        if (pose == null) return null;
        return new float[]{ pose.tx(), pose.ty(), pose.tz() };
    }

    public Anchor createAnchorAtCurrentPose() {
        Pose pose = getCurrentPose();
        if (pose == null || session == null) return null;
        try {
            return session.createAnchor(pose);
        } catch (Exception e) {
            Log.e(TAG, "createAnchor failed: " + e.getMessage());
            return null;
        }
    }

    public void resume() {
        if (session == null) return;
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            notifyError("Camera not available: " + e.getMessage());
        }
    }

    public void pause() {
        if (session != null) session.pause();
    }

    public void destroy() {
        if (session != null) {
            session.close();
            session = null;
            sessionInitialized = false;
        }
    }

    public boolean isSessionInitialized() { return sessionInitialized; }

    public boolean isTracking() {
        return getCurrentPose() != null;
    }

    private void notifyError(String message) {
        Log.e(TAG, message);
        if (listener != null) listener.onSessionError(message);
    }
}