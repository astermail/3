package com.gaspoweredcake.client33.utils.render;

import java.util.ArrayList;
import java.util.List;

/** Frame-local label placement with a hard viewport and overlap boundary. */
public final class LabelLayout {
    public record Rect(double x, double y, double width, double height) {
        public boolean overlaps(Rect other) { return x < other.x + other.width + 4 && x + width + 4 > other.x && y < other.y + other.height + 4 && y + height + 4 > other.y; }
    }
    private final double width, height;
    private final List<Rect> occupied = new ArrayList<>();
    public LabelLayout(double width, double height) { this.width = width; this.height = height; }
    public void reserve(Rect rect) { occupied.add(rect); }
    public Rect place(double centerX, double bottom, double w, double h) {
        if (!Double.isFinite(centerX) || !Double.isFinite(bottom) || !Double.isFinite(w) || !Double.isFinite(h)
            || w <= 0 || h <= 0 || w > width - 8 || h > height - 8 || centerX < 0 || centerX > width || bottom < 0 || bottom > height) return null;
        double x = Math.clamp(centerX - w / 2, 4, width - w - 4);
        for (int attempt = 0; attempt < 4; attempt++) {
            double y = bottom - h - attempt * (h + 5);
            if (y < 4 || y + h > height - 4) continue;
            Rect candidate = new Rect(x, y, w, h);
            boolean overlap = false;
            for (Rect rect : occupied) if (candidate.overlaps(rect)) { overlap = true; break; }
            if (!overlap) { occupied.add(candidate); return candidate; }
        }
        return null;
    }
}
