package fr.chk.alienlive;

import android.graphics.PointF;
import java.util.List;

/** Mesures dans un repère lié aux yeux, indépendantes du roulis et de la distance caméra. */
final class ContourExpressions {
    private float ux, uy, scale, originX, originY;
    private float neutralWidth, neutralLeft, neutralRight;
    private boolean hasLeft, hasRight;
    private long previous;
    boolean lipContours;
    float mouth, rounded, cheekLeft, cheekRight;
    float eyeLeft = -1f, eyeRight = -1f;

    void reset() {
        previous = 0;
        neutralWidth = mouth = rounded = cheekLeft = cheekRight = 0f;
        hasLeft = hasRight = false;
        eyeLeft = eyeRight = -1f;
    }

    void update(List<PointF> leftEye, List<PointF> rightEye, List<PointF> upper,
                List<PointF> lower, List<PointF> leftCheek, List<PointF> rightCheek,
                List<PointF> nose, Float leftProbability, Float rightProbability,
                float yaw, float pitch, float smile, long now) {
        if (previous != 0 && now - previous > 450) reset();
        float dt = previous == 0 ? 50f : Math.max(1f, Math.min(150f, now - previous));
        previous = now;
        boolean aligned = align(leftEye, rightEye);
        eyeLeft = openness(leftEye, leftProbability, aligned, yaw);
        eyeRight = openness(rightEye, rightProbability, aligned, yaw);
        float opening = 0f, pucker = 0f, liftL = 0f, liftR = 0f;
        boolean frontal = aligned && Math.abs(yaw) < 24f && Math.abs(pitch) < 22f;
        lipContours = false;
        if (aligned && upper.size() >= 3 && lower.size() >= 3) {
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (PointF p : upper) {
                float x = along(p);
                min = Math.min(min, x);
                max = Math.max(max, x);
            }
            float width = max - min;
            if (width > .15f) {
                lipContours = true;
                float center = (min + max) * .5f;
                float gap = depthAtCenter(lower, center, width) - depthAtCenter(upper, center, width);
                opening = clamp((gap - .008f) / .32f);
                if (frontal) {
                    if (neutralWidth == 0f && opening < .18f && smile < .4f) neutralWidth = width;
                    float reference = neutralWidth > 0f ? neutralWidth : .76f;
                    pucker = clamp((1f - width / reference - .035f) / .30f);
                    if (opening < .12f && smile < .30f && Math.abs(width / reference - 1f) < .055f)
                        neutralWidth = reference + (width - reference) * gain(dt, 3500f);
                }
            }
        }
        // La pointe du nez fournit une origine verticale commune aux deux pommettes.
        if (frontal && !nose.isEmpty()) {
            float noseY = down(nose.get(nose.size() / 2));
            boolean resting = smile < .38f && opening < .20f && pucker < .25f;
            if (!leftCheek.isEmpty()) {
                float y = down(leftCheek.get(0)) - noseY;
                if (!hasLeft && resting) { neutralLeft = y; hasLeft = true; }
                if (hasLeft) liftL = clamp((neutralLeft - y - .012f) / .12f);
            }
            if (!rightCheek.isEmpty()) {
                float y = down(rightCheek.get(0)) - noseY;
                if (!hasRight && resting) { neutralRight = y; hasRight = true; }
                if (hasRight) liftR = clamp((neutralRight - y - .012f) / .12f);
            }
        }
        mouth += (opening - mouth) * gain(dt, opening > mouth ? 25f : 32f);
        rounded += (pucker - rounded) * gain(dt, 50f);
        cheekLeft += (liftL - cheekLeft) * gain(dt, 65f);
        cheekRight += (liftR - cheekRight) * gain(dt, 65f);
    }

    private boolean align(List<PointF> a, List<PointF> b) {
        if (a.size() < 4 || b.size() < 4) return false;
        float ax = 0, ay = 0, bx = 0, by = 0;
        for (PointF p : a) { ax += p.x; ay += p.y; }
        for (PointF p : b) { bx += p.x; by += p.y; }
        ax /= a.size(); ay /= a.size(); bx /= b.size(); by /= b.size();
        float dx = bx - ax, dy = by - ay;
        scale = (float)Math.hypot(dx, dy);
        if (scale < 12f) return false;
        float sign = dx < 0f ? -1f : 1f;
        ux = sign * dx / scale; uy = sign * dy / scale;
        originX = (ax + bx) * .5f; originY = (ay + by) * .5f;
        return true;
    }
    private float along(PointF p) { return ((p.x-originX)*ux + (p.y-originY)*uy)/scale; }
    private float down(PointF p) { return ((p.y-originY)*ux - (p.x-originX)*uy)/scale; }
    private float depthAtCenter(List<PointF> points, float center, float width) {
        float sum = 0f, weight = 0f, nearest = Float.MAX_VALUE, fallback = 0f;
        for (PointF p : points) {
            float dx = Math.abs(along(p) - center);
            if (dx < nearest) { nearest = dx; fallback = down(p); }
            float w = Math.max(0f, 1f - dx / (width * .34f));
            sum += w * down(p); weight += w;
        }
        return weight > 0f ? sum / weight : fallback;
    }
    private float openness(List<PointF> points, Float probability, boolean aligned, float yaw) {
        boolean classified = probability != null && probability >= 0f;
        if (!aligned || points.size() < 4 || Math.abs(yaw) > 35f)
            return classified ? clamp(probability) : -1f;
        float l = Float.MAX_VALUE, r = -l, top = l, bottom = -l;
        for (PointF p : points) {
            float x = along(p), y = down(p);
            l = Math.min(l,x); r = Math.max(r,x); top = Math.min(top,y); bottom = Math.max(bottom,y);
        }
        float geometry = clamp(((bottom-top)/Math.max(.01f,r-l) - .09f)/.24f);
        return classified ? geometry*.45f + clamp(probability)*.55f : geometry;
    }
    private static float gain(float dt, float tau) { return (float)-Math.expm1(-dt/tau); }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
