package org.openstatic.sound;

import java.io.File;

public interface MixerStreamListener
{
    public void onAudioInput(MixerStream mixerStream);
    public void onDTMF(MixerStream mixerStream, char dtmf);
    public void onDTMFSequence(MixerStream mixerStream, String dtmfSequence);
    public void onSilence(MixerStream mixerStream);
    public void onRecording(MixerStream mixerStream, long recordingDuration, File recordingFile);
    public void onStartup(MixerStream mixerStream);
    public void onShutdown(MixerStream mixerStream);    
}
