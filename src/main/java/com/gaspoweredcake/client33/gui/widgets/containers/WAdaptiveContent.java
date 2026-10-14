package com.gaspoweredcake.client33.gui.widgets.containers;

/** Fits the result viewport after measuring the real header/filter/footer heights. */
public class WAdaptiveContent extends WVerticalList {
    public WView results;
    public double availableHeight;
    @Override protected void onCalculateSize() {
        super.onCalculateSize();
        if (results != null) {
            double fixed = height - results.height;
            results.maxHeight = Math.max(theme.textHeight(), availableHeight - fixed);
            results.calculateSize();
            super.onCalculateSize();
        }
    }
}
