package android.media.audiopolicy;

import android.content.Context;
import android.media.AudioFocusInfo;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Looper;
import java.util.List;

public class AudioPolicy {
    public AudioRecord createAudioRecordSink(AudioMix mix, AudioRecord.Builder audioRecordBuilder) {
        throw new RuntimeException("Stub!");
    }

    public AudioTrack createAudioTrackSource(AudioMix mix, AudioTrack.Builder audioTrackBuilder) {
        throw new RuntimeException("Stub!");
    }

    public List<AudioFocusInfo> getFocusStack() {
        throw new RuntimeException("Stub!");
    }

    public abstract static class AudioPolicyFocusListener {
        public void onAudioFocusGrant(AudioFocusInfo afi, int requestResult) {
            throw new RuntimeException("Stub!");
        }

        public void onAudioFocusLoss(AudioFocusInfo afi, boolean wasNotified) {
            throw new RuntimeException("Stub!");
        }
    }

    public static class Builder {
        public Builder(Context context) {
            throw new RuntimeException("Stub!");
        }

        public Builder addMix(AudioMix mix) {
            throw new RuntimeException("Stub!");
        }

        public void setAudioPolicyFocusListener(AudioPolicyFocusListener l) {
            throw new RuntimeException("Stub!");
        }

        public Builder setLooper(Looper looper) {
            throw new RuntimeException("Stub!");
        }

        public AudioPolicy build() {
            throw new RuntimeException("Stub!");
        }
    }
}
