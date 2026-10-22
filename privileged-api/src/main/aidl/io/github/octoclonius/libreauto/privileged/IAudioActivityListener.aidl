package io.github.octoclonius.libreauto.privileged;

import io.github.octoclonius.libreauto.privileged.AudioFocusHolder;
import io.github.octoclonius.libreauto.privileged.AudioPlayer;

oneway interface IAudioActivityListener {
    void onAudioActivity(in AudioPlayer[] players, in AudioFocusHolder[] focusHolders);
}
