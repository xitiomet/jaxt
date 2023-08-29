package org.openstatic.sound;

import java.io.OutputStream;

import org.json.JSONObject;

public interface MixerStream 
{
    public boolean isAlive();            // Is the input alive?
    public boolean canBeRecorded();      // Can this device be recorded? aka is it a source of audio
    public boolean canPlayTo();          // Can this device be transmitted to or be used as a monitor
    public JSONObject getMixerSettings();
    public void addMP3TargetStream(OutputStream out);
    public void addRawTargetStream(OutputStream out);
    public void removeRawTargetStream(OutputStream out);
    public void addMixerStreamListener(MixerStreamListener l);
    public void removeMixerStreamListener(MixerStreamListener l);
    public void play(byte[] clip);
    public OutputStream getOutputStream();
    public int outputStreamCount();
    public int getNumChannels();
    public float getSampleRate();
    public String getMixerName();
    public void start();
    public void restart();
    public void stop();
    public void setPTT(boolean v);
}
