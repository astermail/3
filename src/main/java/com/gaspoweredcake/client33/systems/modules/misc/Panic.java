package com.gaspoweredcake.client33.systems.modules.misc;

import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import java.util.ArrayList;

public class Panic extends Module {
    public Panic() { super(Categories.Misc, "panic", "Immediately disables every active module. Bind to an emergency key."); runInMainMenu = true; }
    @Override public void onActivate() {
        for (Module module : new ArrayList<>(Modules.get().getActive())) if (module != this) module.disable();
        disable();
    }
}
