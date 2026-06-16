package com.desertkun.brainout;

public class AndroidConstants extends ClientConstants
{
    public static class Touch
    {
        // --- Floating sticks ---------------------------------------------
        // Stick base radius as a fraction of the smaller screen dimension.
        public static final float STICK_RADIUS = 0.13f;
        // How far the knob may travel from the origin, in stick radii.
        public static final float KNOB_RANGE = 1.0f;

        // Left (move) stick: deflection (fraction of radius) past which an axis
        // counts as a full -1/+1, and past which the player starts running.
        public static final float MOVE_THRESHOLD = 0.35f;
        public static final float RUN_THRESHOLD = 0.85f;

        // Right (aim) stick: deflection before we start aiming, and before we
        // auto-fire. AIM_RADIUS_PX is the screen-space magnitude handed to the
        // absolute-aim path (only the direction matters for the aim angle).
        public static final float AIM_DEAD_ZONE = 0.18f;
        public static final float FIRE_THRESHOLD = 0.45f;
        public static final float AIM_RADIUS_PX = 600f;

        // --- Action buttons ----------------------------------------------
        public static final float BUTTON_SIZE = 0.085f;   // fraction of min dim
        public static final float BUTTON_PAD = 0.02f;      // fraction of min dim
    }
}
