package org.openstatic.sound;

import java.io.OutputStream;

import org.json.JSONObject;

public interface MixerStream 
{
    public boolean isAlive();
    public boolean canBeRecorded();
    public boolean canPlayTo();
    public JSONObject getMixerSettings();
    public void addMP3TargetStream(OutputStream out);
    public void addRawTargetStream(OutputStream out);
    public void addMixerStreamListener(MixerStreamListener l);
    public void removeMixerStreamListener(MixerStreamListener l);
    public int outputStreamCount();
    public String getMixerName();
    public void start();
    public void stop();
}
