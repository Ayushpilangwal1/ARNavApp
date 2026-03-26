package com.arnavapp;

import android.util.Log;

import com.google.ar.core.Anchor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WaypointManager
 * ---------------
 * Manages all saved waypoints for a navigation session.
 * Provides add, retrieve, clear, and list operations.
 *
 * Connected to:
 *  - MainActivity: calls saveWaypoint(), clearWaypoints(), getWaypointCount()
 *  - ARCoreManager: receives Anchor objects to store inside each Waypoint
 *  - NavigationEngine: reads the waypoint list via getWaypoints() to navigate
 */
public class WaypointManager {

    private static final String TAG = "WaypointManager";

    private final List<Waypoint> waypoints = new ArrayList<>();

    // Callback so other components can react to waypoint changes
    public interface WaypointListener {
        void onWaypointAdded(Waypoint waypoint, int totalCount);
        void onWaypointsCleared();
    }

    private WaypointListener listener;

    public WaypointManager(WaypointListener listener) {
        this.listener = listener;
    }

    /**
     * Saves a new waypoint using the given ARCore anchor and position.
     * Called by MainActivity when the user taps "Save Waypoint".
     *
     * @param anchor   ARCore Anchor created at the current device pose.
     * @param position float[3] with x,y,z from ARCoreManager.getCurrentPosition()
     * @return The created Waypoint, or null if inputs are invalid.
     */
    public Waypoint saveWaypoint(Anchor anchor, float[] position) {
        if (anchor == null || position == null || position.length < 3) {
            Log.w(TAG, "Cannot save waypoint: invalid anchor or position.");
            return null;
        }

        int index = waypoints.size(); // 0-based
        String label = "Waypoint " + (index + 1);

        Waypoint waypoint = new Waypoint(anchor, position, label, index);
        waypoints.add(waypoint);

        Log.d(TAG, "Saved " + waypoint);

        if (listener != null) {
            listener.onWaypointAdded(waypoint, waypoints.size());
        }

        return waypoint;
    }

    /**
     * Returns an unmodifiable view of all saved waypoints.
     * Called by NavigationEngine at the start of navigation.
     */
    public List<Waypoint> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    /**
     * Returns the waypoint at the given index, or null if out of bounds.
     */
    public Waypoint getWaypoint(int index) {
        if (index < 0 || index >= waypoints.size()) return null;
        return waypoints.get(index);
    }

    /** Returns the total number of saved waypoints. */
    public int getWaypointCount() {
        return waypoints.size();
    }

    /** Returns true if there is at least one waypoint. */
    public boolean hasWaypoints() {
        return !waypoints.isEmpty();
    }

    /**
     * Detaches all ARCore anchors and clears the list.
     * Call when starting a fresh recording session.
     */
    public void clearWaypoints() {
        for (Waypoint wp : waypoints) {
            wp.detach();
        }
        waypoints.clear();
        Log.d(TAG, "All waypoints cleared.");

        if (listener != null) {
            listener.onWaypointsCleared();
        }
    }

    /**
     * Returns a summary string for display / logging.
     */
    public String getSummary() {
        if (waypoints.isEmpty()) return "No waypoints saved.";
        StringBuilder sb = new StringBuilder();
        sb.append(waypoints.size()).append(" waypoints:\n");
        for (Waypoint wp : waypoints) {
            sb.append("  ").append(wp.toString()).append("\n");
        }
        return sb.toString();
    }
}
