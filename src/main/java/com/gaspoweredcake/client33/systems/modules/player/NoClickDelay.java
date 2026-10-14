package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;

public class NoClickDelay extends Module {
    public NoClickDelay() { super(Categories.Player, "no-click-delay", "Removes the client delay after missed attacks; weapon cooldown still applies.", "NoClickDelay"); }
}
