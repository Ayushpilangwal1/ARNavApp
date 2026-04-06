package com.arnavapp;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.google.ar.core.Anchor;
import com.google.ar.core.Pose;

/**
 * CameraController
 * ---------------
 * Manages camera interactions and waypoint marking through touch gestures.
 * Provides visual feedback for camera tracking and waypoint marking.
 *
 * Features:
 * - Single tap to mark waypoint at current camera pose
 * - Double tap to confirm waypoint
 * - Continuous tracking visualization
 * - Distance and position feedback
 */
public class CameraController implements View.OnTouchListener {

    private static final String TAG = "CameraController";
    
    // Tap detection thresholds
    private static final long DOUBLE_TAP_TIME_DELTA = 300; // milliseconds
    private static final float TAP_DISTANCE_THRESHOLD = 50; // pixels
    
    // Waypoint marking state
    private long lastTapTime = 0;
    private float lastTapX = 0;
    private float lastTapY = 0;
    private boolean isWaypointPending = false;
    
    // Dependencies
    private final ARCoreManager arCoreManager;
    private final WaypointManager waypointManager;
    private final AudioManager audioManager;
    private final Activity activity;
    
    // Callback interface
    public interface CameraControllerListener {
        void onWaypointMarked(float[] position);
        void onWaypointConfirmed(Waypoint waypoint);
        void onCameraStatusChanged(String status);
        void onTapFeedback(float x, float y);
    }
    
    private CameraControllerListener listener;
    
    public CameraController(
            Activity activity,
            ARCoreManager arCoreManager,
            WaypointManager waypointManager,
            AudioManager audioManager,
            CameraControllerListener listener) {
        this.activity = activity;
        this.arCoreManager = arCoreManager;
        this.waypointManager = waypointManager;
        this.audioManager = audioManager;
        this.listener = listener;
    }
    
    /**
     * Starts camera tracking and initializes waypoint marking mode.
     * Should be called when user presses "Start Recording" button.
     */
    public void startCameraTracking() {
        Log.d(TAG, "Starting camera tracking for waypoint marking");
        if (listener != null) {
            listener.onCameraStatusChanged("Camera tracking active - tap screen to mark waypoints");
        }
    }
    
    /**
     * Stops camera tracking and resets waypoint marking mode.
     */
    public void stopCameraTracking() {
        Log.d(TAG, "Stopping camera tracking");
        isWaypointPending = false;
        if (listener != null) {
            listener.onCameraStatusChanged("Camera tracking stopped");
        }
    }
    
    /**
     * Marks a waypoint at the current camera position.
     * Called on single tap when recording is active.
     *
     * @return true if waypoint was successfully created, false otherwise
     */
    public boolean markWaypointAtCurrentPose() {
        // Get current pose from ARCore
        Pose currentPose = arCoreManager.getCurrentPose();
        if (currentPose == null) {
            Log.w(TAG, "Cannot mark waypoint: pose is null");
            return false;
        }
        
        // Create anchor at current pose
        Anchor anchor = arCoreManager.createAnchorAtCurrentPose();
        if (anchor == null) {
            Log.w(TAG, "Cannot mark waypoint: failed to create anchor");
            return false;
        }
        
        // Get position
        float[] position = currentPose.getTranslation();
        
        // Save waypoint
        Waypoint waypoint = waypointManager.saveWaypoint(anchor, position);
        if (waypoint != null) {
            isWaypointPending = true;
            Log.d(TAG, "Waypoint marked at: " + position[0] + ", " + position[1] + ", " + position[2]);
            
            if (listener != null) {
                listener.onWaypointMarked(position);
            }
            
            if (audioManager != null) {
                int waypointNumber = waypointManager.getWaypointCount();
                audioManager.announceWaypointSaved(waypointNumber);
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Confirms the pending waypoint on double tap.
     */
    public void confirmPendingWaypoint() {
        if (!isWaypointPending) return;
        
        int waypointIndex = waypointManager.getWaypointCount() - 1;
        Waypoint waypoint = waypointManager.getWaypoint(waypointIndex);
        
        if (waypoint != null) {
            isWaypointPending = false;
            Log.d(TAG, "Waypoint confirmed: " + waypoint.getLabel());
            
            if (listener != null) {
                listener.onWaypointConfirmed(waypoint);
            }
            
            if (audioManager != null) {
                audioManager.speak("Waypoint confirmed");
            }
        }
    }
    
    /**
     * Handles touch events for waypoint marking.
     * Single tap = mark waypoint
     * Double tap = confirm waypoint
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float currentX = event.getX();
            float currentY = event.getY();
            long currentTime = System.currentTimeMillis();
            
            // Check if this is a double tap
            if (isDoubleTap(currentX, currentY, currentTime)) {
                Log.d(TAG, "Double tap detected - confirming waypoint");
                confirmPendingWaypoint();
                return true;
            }
            
            // Single tap - mark waypoint
            Log.d(TAG, "Single tap detected - marking waypoint");
            if (markWaypointAtCurrentPose()) {
                if (listener != null) {
                    listener.onTapFeedback(currentX, currentY);
                }
            }
            
            lastTapX = currentX;
            lastTapY = currentY;
            lastTapTime = currentTime;
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if the current tap is a double tap based on timing and distance.
     */
    private boolean isDoubleTap(float x, float y, long time) {
        long timeDelta = time - lastTapTime;
        float distanceDelta = (float) Math.sqrt(
                Math.pow(x - lastTapX, 2) + Math.pow(y - lastTapY, 2)
        );
        
        return timeDelta < DOUBLE_TAP_TIME_DELTA && distanceDelta < TAP_DISTANCE_THRESHOLD;
    }
    
    /**
     * Gets the current camera position in world space.
     *
     * @return float array [x, y, z] or null if not tracking
     */
    public float[] getCurrentCameraPosition() {
        return arCoreManager.getCurrentPosition();
    }
    
    /**
     * Gets the current camera pose including rotation.
     *
     * @return Pose object or null if not tracking
     */
    public Pose getCurrentCameraPose() {
        return arCoreManager.getCurrentPose();
    }
    
    /**
     * Checks if camera is currently tracking.
     *
     * @return true if tracking, false otherwise
     */
    public boolean isCameraTracking() {
        return arCoreManager.isTracking();
    }
    
    /**
     * Gets the distance between two positions.
     *
     * @param pos1 first position [x, y, z]
     * @param pos2 second position [x, y, z]
     * @return distance in meters
     */
    public static float getDistance(float[] pos1, float[] pos2) {
        if (pos1 == null || pos2 == null || pos1.length < 3 || pos2.length < 3) {
            return -1;
        }
        
        float dx = pos1[0] - pos2[0];
        float dy = pos1[1] - pos2[1];
        float dz = pos1[2] - pos2[2];
        
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Resets waypoint marking state.
     */
    public void resetWaypointMarking() {
        isWaypointPending = false;
        lastTapTime = 0;
        lastTapX = 0;
        lastTapY = 0;
    }
    
    public boolean isWaypointPending() {
        return isWaypointPending;
    }
}

