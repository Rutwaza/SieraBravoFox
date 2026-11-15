package com.nexrtz.plugin;

import android.content.Context;

public interface PluginEntry {
    String start(Context ctx, String slot);
    default void stop() {}
    default void flip() {}
}