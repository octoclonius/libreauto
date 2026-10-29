package io.github.octoclonius.libreauto.privileged.api;

import io.github.octoclonius.libreauto.privileged.api.AudioFocusHolder;
import io.github.octoclonius.libreauto.privileged.api.AudioPlayer;

oneway interface IAudioActivityListener {
    void onAudioActivity(in AudioPlayer[] players, in AudioFocusHolder[] focusHolders);

    void onMicrophoneActivity(boolean isRecording);
}
