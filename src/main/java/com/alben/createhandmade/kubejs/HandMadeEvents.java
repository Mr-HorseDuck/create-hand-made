package com.alben.createhandmade.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface HandMadeEvents {

    EventGroup GROUP = EventGroup.of("HandMadeEvents");

    EventHandler TOOL_FILTER = GROUP.server(
            "toolFilter",
            () -> HandMadeToolFilterKubeEvent.class
    );
}