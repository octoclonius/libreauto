package io.github.octoclonius.libreauto.privileged;

import io.github.octoclonius.libreauto.privileged.DisplayTask;

oneway interface IDisplayTaskListener {
    void onTasks(in DisplayTask[] tasks);
}
