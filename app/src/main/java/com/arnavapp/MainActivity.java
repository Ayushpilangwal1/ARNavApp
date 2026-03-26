package com.arnavapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.Anchor;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;

import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity
        implements ARCoreManager.ARCoreListener,
        WaypointManager.WaypointListener,
        NavigationEngine.NavigationListener,
        AudioManager.AudioReadyListener,
        GLSurfaceView.Renderer {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 1001;

    // Modules
    private ARCoreManager arCoreManager;
    private WaypointManager waypointManager;
    private NavigationEngine navigationEngine;
    private AudioManager audioManager;

    // UI
    private GLSurfaceView surfaceView;
    private Button btnStartRecording;
    private Button btnSaveWaypoint;
    private Button btnStartNavigation;
    private TextView tvStatus;
    private TextView tvPosition;
    private TextView tvWaypointCount;

    // State
    private enum AppState { IDLE, RECORDING, NAVIGATING }
    private AppState appState = AppState.IDLE;
    private TrackingState lastTrackingState = TrackingState.STOPPED;

    private boolean arSessionReady = false;
    private int backgroundTextureId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();

        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        audioManager    = new AudioManager(this, this);
        waypointManager = new WaypointManager(this);
        navigationEngine= new NavigationEngine(this);
        arCoreManager   = new ARCoreManager(this);

        setupButtonListeners();
        setStatus("Checking camera permission...");
        updateUI();

        if (!hasCameraPermission()) requestCameraPermission();
        else tryInitARCore();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arSessionReady) arCoreManager.resume();
        surfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        surfaceView.onPause();
        if (arSessionReady) arCoreManager.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (arCoreManager != null) arCoreManager.destroy();
        if (audioManager != null)  audioManager.shutdown();
    }

    private void tryInitARCore() {
        setStatus("Initializing ARCore...");
        if (arCoreManager.initSession(this)) {
            arSessionReady = true;
            arCoreManager.resume();
            setStatus("ARCore ready. Move phone to start tracking.");
            updateUI();
        } else {
            setStatus("ARCore install requested. Please install and reopen.");
        }
    }

    private void bindViews() {
        surfaceView        = findViewById(R.id.surfaceView);
        btnStartRecording  = findViewById(R.id.btnStartRecording);
        btnSaveWaypoint    = findViewById(R.id.btnSaveWaypoint);
        btnStartNavigation = findViewById(R.id.btnStartNavigation);
        tvStatus           = findViewById(R.id.tvStatus);
        tvPosition         = findViewById(R.id.tvPosition);
        tvWaypointCount    = findViewById(R.id.tvWaypointCount);
    }

    private void setupButtonListeners() {
        btnStartRecording.setOnClickListener(v -> {
            if (!arSessionReady || !arCoreManager.isTracking()) {
                showToast("ARCore not ready/tracking.");
                return;
            }

            if (appState == AppState.RECORDING) {
                // STOP RECORDING
                appState = AppState.IDLE;
                audioManager.speak("Recording stopped. Ready to navigate.");
                updateUI();
            } else {
                // START RECORDING
                if (navigationEngine.isNavigating()) navigationEngine.stopNavigation();
                waypointManager.clearWaypoints();
                appState = AppState.RECORDING;
                audioManager.announceRecordingStarted();
                updateUI();
            }
        });

        btnSaveWaypoint.setOnClickListener(v -> {
            if (appState != AppState.RECORDING || !arCoreManager.isTracking()) return;
            Anchor anchor = arCoreManager.createAnchorAtCurrentPose();
            float[] position = arCoreManager.getCurrentPosition();
            if (anchor != null && position != null) {
                waypointManager.saveWaypoint(anchor, position);
                audioManager.speak("Manual waypoint saved.");
            }
        });

        btnStartNavigation.setOnClickListener(v -> {
            if (!waypointManager.hasWaypoints() || !arCoreManager.isTracking()) return;
            appState = AppState.NAVIGATING;
            List<Waypoint> waypoints = waypointManager.getWaypoints();
            navigationEngine.startNavigation(waypoints);
            audioManager.announceNavigationStarted(waypoints.size());
            updateUI();
        });
    }

    @Override
    public void onTrackingStateChanged(TrackingState state) {
        if (state == lastTrackingState) return;
        lastTrackingState = state;
        runOnUiThread(() -> {
            switch (state) {
                case TRACKING:
                    setStatus("Tracking active ✓ — ready to record.");
                    updateUI();
                    break;
                case PAUSED:
                    setStatus("Tracking paused — move phone slowly.");
                    break;
                case STOPPED:
                    setStatus("Tracking stopped.");
                    break;
            }
        });
    }

    @Override
    public void onSessionError(String errorMessage) {
        runOnUiThread(() -> setStatus("Error: " + errorMessage));
    }

    @Override
    public void onWaypointAdded(Waypoint waypoint, int totalCount) {
        runOnUiThread(() -> {
            tvWaypointCount.setText("Waypoints saved: " + totalCount);
            updateUI();
        });
    }

    @Override
    public void onWaypointsCleared() {
        runOnUiThread(() -> {
            tvWaypointCount.setText("Waypoints saved: 0");
            updateUI();
        });
    }

    @Override
    public void onNavigationInstruction(String instruction, float distance, int targetIndex, int total) {
        String statusText = String.format("WP %d/%d — %.1f m away\n%s", targetIndex + 1, total, distance, instruction);
        runOnUiThread(() -> setStatus(statusText));

        int distanceCm = Math.round(distance * 100);
        if (distanceCm > 150) {
            audioManager.speak(instruction + " for " + Math.round(distance) + " meters");
        } else {
            audioManager.speak(instruction);
        }
    }

    @Override
    public void onWaypointReached(int reachedIndex, int nextIndex) {
        runOnUiThread(() -> {
            if (nextIndex == -1) setStatus("Route complete!");
            else setStatus("WP " + (reachedIndex + 1) + " reached → heading to WP " + (nextIndex + 1));
        });
        audioManager.speakWaypointReached(reachedIndex, nextIndex);
    }

    @Override
    public void onNavigationComplete() {
        runOnUiThread(() -> { appState = AppState.IDLE; updateUI(); });
    }

    @Override
    public void onNavigationError(String message) {
        runOnUiThread(() -> showToast(message));
    }

    @Override public void onAudioReady() { }
    @Override public void onAudioError(String message) { runOnUiThread(() -> showToast("Audio: " + message)); }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            tryInitARCore();
        } else {
            showToast("Camera permission required.");
        }
    }

    private void updateUI() {
        if (appState == AppState.RECORDING) {
            btnStartRecording.setText("Stop Recording");
            btnStartRecording.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))); // Red
        } else {
            btnStartRecording.setText("Start Recording");
            btnStartRecording.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))); // Green
        }

        btnStartRecording.setEnabled(appState != AppState.NAVIGATING && arSessionReady && lastTrackingState == TrackingState.TRACKING);
        btnSaveWaypoint.setEnabled(appState == AppState.RECORDING);

        btnStartNavigation.setEnabled(appState == AppState.IDLE &&
                waypointManager != null &&
                waypointManager.hasWaypoints() &&
                lastTrackingState == TrackingState.TRACKING);
    }

    private void setStatus(String msg) {
        if (tvStatus != null) tvStatus.setText(msg);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        backgroundTextureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, backgroundTextureId);
        arCoreManager.setCameraTextureName(backgroundTextureId);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        arCoreManager.setDisplayGeometry(gl, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (!arSessionReady) return;

        arCoreManager.update();

        Pose pose = arCoreManager.getCurrentPose();
        if (pose != null) {
            float[] currentPos = pose.getTranslation();

            runOnUiThread(() -> tvPosition.setText(String.format("X=%.2f  Y=%.2f  Z=%.2f",
                    currentPos[0], currentPos[1], currentPos[2])));

            // --- BREADCRUMB AUTO-DROP LOGIC ---
            if (appState == AppState.RECORDING && waypointManager != null) {
                List<Waypoint> wps = waypointManager.getWaypoints();
                if (wps != null && !wps.isEmpty()) {
                    Waypoint lastWp = wps.get(wps.size() - 1);
                    if (lastWp.getAnchor() != null) {
                        float[] lastPos = lastWp.getAnchor().getPose().getTranslation();
                        float dx = currentPos[0] - lastPos[0];
                        float dz = currentPos[2] - lastPos[2];
                        float distFromLast = (float) Math.sqrt((dx * dx) + (dz * dz));

                        if (distFromLast >= 1.2f) {
                            Anchor anchor = arCoreManager.createAnchorAtCurrentPose();
                            if (anchor != null) {
                                waypointManager.saveWaypoint(anchor, currentPos);
                                runOnUiThread(() -> setStatus("Breadcrumb auto-dropped!"));
                            }
                        }
                    }
                }
            }

            // --- NAVIGATION LOGIC ---
            if (appState == AppState.NAVIGATING) {
                navigationEngine.update(pose);
            }
        }
    }
}