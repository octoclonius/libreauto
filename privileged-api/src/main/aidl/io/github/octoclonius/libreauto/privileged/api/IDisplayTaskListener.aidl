package io.github.octoclonius.libreauto.privileged.api;

import io.github.octoclonius.libreauto.privileged.api.DisplayTask;

oneway interface IDisplayTaskListener {
    void onTasks(in DisplayTask[] tasks);
}
