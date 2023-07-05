package org.openstatic.sound;

public interface MixerStreamListener
{
    public void onAudioStart(MixerStream mixerStream);
    public void onSilence(MixerStream mixerStream);
    public void onShutdown(MixerStream mixerStream);    
}
