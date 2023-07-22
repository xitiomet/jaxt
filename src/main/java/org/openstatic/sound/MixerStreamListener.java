package org.openstatic.sound;

public interface MixerStreamListener
{
    public void onAudioInput(MixerStream mixerStream);
    public void onDTMF(MixerStream mixerStream, char dtmf);
    public void onSilence(MixerStream mixerStream);
    public void onStartup(MixerStream mixerStream);
    public void onShutdown(MixerStream mixerStream);    
}
