package coralintel.module;

public class DropdownSetting extends Setting {

    private final String[] options;
    private int index;
    private boolean open = false;

    public DropdownSetting(String name, int defaultIndex, String... options) {
        super(name);
        this.options = options;
        this.index   = defaultIndex;
    }

    public DropdownSetting(String name, int defaultIndex, java.util.function.BooleanSupplier visibility, String... options) {
        super(name, visibility);
        this.options = options;
        this.index   = defaultIndex;
    }

    public String getValue()    { return options[index]; }
    public int    getIndex()    { return index; }
    public String[] getOptions(){ return options; }
    public boolean isOpen()     { return open; }
    public void setOpen(boolean o) { open = o; }

    public void next() {
        index = (index + 1) % options.length;
    }

    public void prev() {
        index = (index - 1 + options.length) % options.length;
    }

    public void setIndex(int i) {
        if (i >= 0 && i < options.length) index = i;
    }
}
