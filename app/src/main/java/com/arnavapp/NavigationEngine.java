package com.arnavapp;

import com.google.ar.core.Pose;
import java.util.List;

public class NavigationEngine {

    public interface NavigationListener {
        void onNavigationInstruction(String instruction, float distance, int targetIndex, int total);
        void onWaypointReached(int reachedIndex, int nextIndex);
        void onNavigationComplete();
        void onNavigationError(String message);
    }

    private final NavigationListener listener;
    private List<Waypoint> waypoints;
    private int currentTargetIndex = 0;
    private boolean isNavigating = false;

    // Cooldown variables to stop TTS spam
    private long lastAnnouncementTime = 0;
    private static final long ANNOUNCEMENT_COOLDOWN_MS = 3500; // Speak every 3.5 seconds

    public NavigationEngine(NavigationListener listener) {
        this.listener = listener;
    }

    public void startNavigation(List<Waypoint> waypoints) {
        this.waypoints = waypoints;
        this.currentTargetIndex = 0;
        this.isNavigating = true;
        this.lastAnnouncementTime = 0; // reset
    }

    public void stopNavigation() {
        this.isNavigating = false;
    }

    public boolean isNavigating() {
        return isNavigating;
    }

    public void update(Pose currentCameraPose) {
        if (!isNavigating || waypoints == null || currentTargetIndex >= waypoints.size()) return;

        Waypoint targetWaypoint = waypoints.get(currentTargetIndex);
        if (targetWaypoint.getAnchor() == null) return;

        // 1. Get dynamically updated positions to fight AR drift
        float[] currentPos = currentCameraPose.getTranslation();
        float[] targetPos = targetWaypoint.getAnchor().getPose().getTranslation();

        // 2. Calculate Distance
        float dx = targetPos[0] - currentPos[0];
        float dz = targetPos[2] - currentPos[2];
        float distance = (float) Math.sqrt((dx * dx) + (dz * dz));

        // 3. Tighter Reach Radius for Breadcrumbs (1.0 meter)
        if (distance < 1.0f) {
            int reachedIndex = currentTargetIndex;
            currentTargetIndex++;
            lastAnnouncementTime = 0; // Speak immediately for the next point

            if (currentTargetIndex >= waypoints.size()) {
                isNavigating = false;
                listener.onWaypointReached(reachedIndex, -1);
                listener.onNavigationComplete();
            } else {
                listener.onWaypointReached(reachedIndex, currentTargetIndex);
            }
            return;
        }

        // 4. Voice Spam Control (Debounce)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnnouncementTime < ANNOUNCEMENT_COOLDOWN_MS) {
            return;
        }
        lastAnnouncementTime = currentTime;

        // 5. Bulletproof Left/Right Math (Dot and Cross Product)
        float[] forwardPoint = currentCameraPose.transformPoint(new float[]{0, 0, -1f});

        float forwardX = forwardPoint[0] - currentPos[0];
        float forwardZ = forwardPoint[2] - currentPos[2];

        double det = (forwardX * dz) - (forwardZ * dx);
        double dot = (forwardX * dx) + (forwardZ * dz);

        // Positive = Target is to the Right. Negative = Target is to the Left.
        double angleDifference = Math.toDegrees(Math.atan2(det, dot));

        // 6. Simple Relative Directions
        String instruction;
        if (angleDifference > 50) {
            instruction = "Turn right";
        } else if (angleDifference > 20) {
            instruction = "Turn slightly right";
        } else if (angleDifference < -50) {
            instruction = "Turn left";
        } else if (angleDifference < -20) {
            instruction = "Turn slightly left";
        } else {
            instruction = "Continue straight";
        }

        listener.onNavigationInstruction(instruction, distance, currentTargetIndex, waypoints.size());
    }
}