package io.github.wiojelt.dotsuite.drawing;

import android.graphics.Path;
import android.graphics.Matrix;
import io.github.wiojelt.dotsuite.data.BackArrowPolicy;

/** Original filled geometry. No Canvas/Paint mutation, animator, resource or frame allocation. */
public final class BackArrowRenderer {
    private BackArrowRenderer() {}
    private static final ThreadLocal<Matrix> TRANSFORM = ThreadLocal.withInitial(Matrix::new);
    public static boolean draw(Path out, int style, int motion, int size,
            float x, float y, float stroke, float referenceLength) {
        // Returning false leaves the caller's original path intact.
        if (style < 1 || style > 15 || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(stroke) || x < 0 || y < 0 || stroke <= 0
                || x > 1000 || y > 1000 || stroke > 100) return false;
        float progress = BackArrowPolicy.progress(x, referenceLength);
        float scale = Math.max(80, Math.min(120, size)) / 100f
                * BackArrowPolicy.motionScale(motion, progress);
        x *= scale;
        y *= scale * (motion == 2 ? .45f + .55f * progress : 1);
        float w = stroke * scale * .65f;
        if (motion == 4) {
            float spring = (float) Math.sin(Math.PI * progress);
            x *= 1 - .2f * spring;
            y *= 1 + .1f * spring;
        }
        if (motion == 5) x *= .35f + .65f * progress;
        if (motion == 6) w *= .55f + .45f * progress;
        if (motion == 10) { x *= .7f + .3f * progress; y *= 1 - .15f * (1 - progress); }
        out.rewind();
        out.setFillType(Path.FillType.WINDING);
        if (style == 1 || style == 2) {
            line(out, 0, 0, x, -y, w);
            line(out, 0, 0, x, y, w);
            if (style == 2) line(out, 0, 0, x * 1.3f, 0, w);
        } else if (style == 3) {
            line(out, 0, 0, x * .6f, -y, w * .8f);
            line(out, 0, 0, x * .6f, y, w * .8f);
            line(out, x * .6f, 0, x * 1.2f, -y, w * .8f);
            line(out, x * .6f, 0, x * 1.2f, y, w * .8f);
        } else if (style == 4 || style == 5) {
            for (int i = -3; i <= 3; i++) {
                float px = x * Math.abs(i) / 3, py = y * i / 3;
                if (style == 4) out.addRect(px - w, py - w, px + w, py + w, Path.Direction.CW);
                else {
                    float r = w * (1.3f - Math.abs(i) * .18f);
                    out.addCircle(px, py, r, Path.Direction.CW);
                }
            }
        } else if (style == 6) {
            // A curved ribbon, kept inside the same small indicator envelope.
            out.moveTo(x, -y - w);
            out.cubicTo(x * .45f, -y * .8f, -w, -y * .4f, -w, 0);
            out.cubicTo(-w, y * .4f, x * .45f, y * .8f, x, y + w);
            out.lineTo(x, y - w);
            out.cubicTo(x * .5f, y * .55f, w, y * .3f, w, 0);
            out.cubicTo(w, -y * .3f, x * .5f, -y * .55f, x, -y + w);
            out.close();
        } else if (style == 7) {
            out.moveTo(-w, 0); out.lineTo(x + w, -y - w);
            out.lineTo(x + w, y + w); out.close();
        } else if (style == 8) {
            // Outline badge plus a directional mark; no solid background or contrast override.
            out.addOval(-2 * w, -y - w, x * 1.2f + w, y + w, Path.Direction.CW);
            if (x > 3 * w && y > 2 * w)
                out.addOval(0, -y + w, x * 1.2f - w, y - w, Path.Direction.CCW);
            line(out, x * .25f, 0, x * .65f, -y * .45f, w * .7f);
            line(out, x * .25f, 0, x * .65f, y * .45f, w * .7f);
        } else if (style == 9) {
            line(out, 0, -y, 0, y, w);
            line(out, 0, -y, x, -y, w);
            line(out, 0, y, x, y, w);
        } else if (style == 10) {
            line(out, 0, 0, x, -y, w * .48f); line(out, 0, 0, x, y, w * .48f);
        } else if (style == 11) {
            line(out, 0, 0, x * .8f, -y * .75f, w * 1.1f);
            line(out, 0, 0, x * .8f, y * .75f, w * 1.1f);
        } else if (style == 12) {
            out.moveTo(-w, 0); out.lineTo(x, -y);
            out.lineTo(x * .55f, 0); out.lineTo(x, y); out.close();
            out.addCircle(0, 0, w * .4f, Path.Direction.CW);
        } else if (style == 13) {
            line(out, 0, 0, x * .7f, -y * .75f, w * .6f);
            line(out, 0, 0, x * .7f, y * .75f, w * .6f);
            out.addCircle(x * 1.1f, 0, w * 1.5f, Path.Direction.CW);
        } else if (style == 14) {
            line(out, 0, 0, x, -y, w * .8f); line(out, 0, 0, x, y, w * .8f);
            line(out, x * .4f, 0, x * 1.2f, 0, w * .55f);
        } else {
            for (int i = 0; i < 3; i++) {
                float start = i * x * .35f;
                line(out, start, 0, start + x * .5f, -y * .65f, w * .6f);
                line(out, start, 0, start + x * .5f, y * .65f, w * .6f);
            }
        }
        if (motion == 7 || motion == 11 || motion == 8 || motion == 9) {
            Matrix matrix = TRANSFORM.get();
            if (motion == 8) matrix.setTranslate(x * .2f * (1 - progress), 0);
            else if (motion == 9) matrix.setTranslate(0, -y * .2f * (1 - progress));
            else matrix.setRotate((motion == 11 ? 10 : -12) * (1 - progress), x * .5f, 0);
            out.transform(matrix);
        }
        return true;
    }
    private static void line(Path p, float x1, float y1, float x2, float y2, float radius) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length > .001f) {
            float nx = -dy / length * radius, ny = dx / length * radius;
            // Match CW end caps: opposite winding cuts holes at the joins under WINDING fill.
            p.moveTo(x1 - nx, y1 - ny); p.lineTo(x2 - nx, y2 - ny);
            p.lineTo(x2 + nx, y2 + ny); p.lineTo(x1 + nx, y1 + ny); p.close();
        }
        p.addCircle(x1, y1, radius, Path.Direction.CW);
        p.addCircle(x2, y2, radius, Path.Direction.CW);
    }
}
