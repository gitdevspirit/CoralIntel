package coralintel.module;

public class SliderSetting extends Setting {
    private double value;
    private final double min;
    private final double max;
    private final double step;

    public SliderSetting(String name, double value, double min, double max, double step) {
        super(name);
        this.value = value;
        this.min   = min;
        this.max   = max;
        this.step  = step;
    }

    public SliderSetting(String name, double value, double min, double max, double step, java.util.function.BooleanSupplier visibility) {
        super(name, visibility);
        this.value = value;
        this.min   = min;
        this.max   = max;
        this.step  = step;
    }

    public double getValue() { return value; }
    public double getMin()   { return min; }
    public double getMax()   { return max; }

    public void setValue(double v) {
        // snap to step, clamp to range
        double stepped = Math.round(v / step) * step;
        this.value = Math.max(min, Math.min(max, stepped));
    }

    // Returns 0.0–1.0 for rendering the fill bar
    public float getPercent() {
        return (float)((value - min) / (max - min));
    }
}
