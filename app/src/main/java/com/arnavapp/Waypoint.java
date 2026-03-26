package com.arnavapp;

import com.google.ar.core.Anchor;

/**
 * Waypoint
 * --------
 * Data model representing a single saved navigation point.
 *
 * Each waypoint stores:
 *  - An ARCore Anchor (tracks the real-world position even as the phone moves)
 *  - A snapshot of the x,y,z position at save time (for distance calculations)
 *  - An optional name/label for voice guidance
 *  - An index for ordering
 */
public class Waypoint {

    private final Anchor anchor;       // ARCore anchor keeping this point in 3D space
    private final float[] position;    // [x, y, z] at time of saving
    private final String label;        // Voice label e.g. "Waypoint 1"
    private final int index;           // Sequential index in the waypoint list

    public Waypoint(Anchor anchor, float[] position, String label, int index) {
        this.anchor = anchor;
        this.position = position.clone(); // defensive copy
        this.label = label;
        this.index = index;
    }

    /**
     * Returns the ARCore Anchor for this waypoint.
     * The anchor is used to get an updated pose if the AR session re-localizes.
     */
    public Anchor getAnchor() {
        return anchor;
    }

    /**
     * Returns the [x, y, z] position recorded when waypoint was saved.
     * Used by NavigationEngine for Euclidean distance calculations.
     */
    public float[] getPosition() {
        return position.clone();
    }

    /**
     * Returns the best available position for this waypoint.
     * Prefers live anchor pose if tracking; falls back to saved position.
     */
    public float[] getBestPosition() {
        if (anchor != null && anchor.getTrackingState() == com.google.ar.core.TrackingState.TRACKING) {
            com.google.ar.core.Pose pose = anchor.getPose();
            return new float[]{pose.tx(), pose.ty(), pose.tz()};
        }
        return getPosition();
    }

    public String getLabel() {
        return label;
    }

    public int getIndex() {
        return index;
    }

    /** Detach the anchor to free ARCore memory when the waypoint is no longer needed. */
    public void detach() {
        if (anchor != null) {
            anchor.detach();
        }
    }

    @Override
    public String toString() {
        return String.format("Waypoint{index=%d, label='%s', pos=[%.2f, %.2f, %.2f]}",
            index, label, position[0], position[1], position[2]);
    }
}
