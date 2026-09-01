package io.github.wiojelt.dotsuite.data;

/** A vertical shade drag must always win over a notch shortcut. Distances are in pixels. */
public final class GesturePolicy {
    private GesturePolicy() {}
    public static final int STILL = 0, SHADE = 1, LEFT = 2, RIGHT = 3, CANCEL = 4;
    public static int motion(float dx, float dy, int pointers, float slop, float swipe) {
        if (pointers != 1) return CANCEL;
        if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) return SHADE;
        if (Math.abs(dx) >= swipe && Math.abs(dx) > Math.abs(dy) * 1.5f) {
            return dx < 0 ? LEFT : RIGHT;
        }
        return STILL;
    }
    public static boolean tap(float dx, float dy, float slop) {
        return dx * dx + dy * dy <= slop * slop;
    }
}
