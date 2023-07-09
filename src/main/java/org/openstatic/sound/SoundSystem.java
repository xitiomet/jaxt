package org.openstatic.sound;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openstatic.kiss.JavaKISSMain;

public class SoundSystem 
{
    ArrayList<MixerStream> availableMixerStreams;
    ArrayList<MixerStream> allMixerStreams;
    HashMap<Mixer.Info, MixerStreamHardware> mixerHardwareStreams;
    HashMap<String, MixerStreamProcess> mixerProcessStreams;
    JSONObject audioSettings;

    public SoundSystem()
    {
        this.audioSettings = JavaKISSMain.settings.optJSONObject("audio", new JSONObject());
        this.mixerHardwareStreams = new HashMap<Mixer.Info, MixerStreamHardware>();
        this.mixerProcessStreams = new HashMap<String, MixerStreamProcess>();
        refreshMixers();
        this.availableMixerStreams.forEach((mixerStream) -> {
            JSONObject mixerStreamSettings = mixerStream.getMixerSettings();
            if (mixerStreamSettings.optBoolean("autoStart", false))
            {
                if (!mixerStream.isAlive())
                    mixerStream.start();
            }
        });
    }

    public void refreshMixers()
    {
        this.allMixerStreams = new ArrayList<MixerStream>();
        this.availableMixerStreams = new ArrayList<MixerStream>();
        boolean hideUndefined = this.audioSettings.optBoolean("hideUndefined", false);
        HashMap<String, JSONObject> definedDevices = new HashMap<String, JSONObject>();
        if (this.audioSettings.has("devices"))
        {
            JSONArray devices = this.audioSettings.getJSONArray("devices");
            devices.forEach((dsn) -> {
                JSONObject jo = (JSONObject) dsn;
                if (jo.has("hardwareName") && jo.optString("type", "").equals("hardware"))
                {
                    definedDevices.put(jo.optString("hardwareName"), jo);
                }
            });
        }
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers)
        {
            String mixerHardwareName = mixerInfo.getName();
            try
            {
                boolean defined = false;
                JSONObject mSettings = new JSONObject();
                mSettings.put("name", mixerHardwareName);
                mSettings.put("type", "hardware");
                mSettings.put("hardwareName", mixerHardwareName);
                // Check to see if the mixer is defined in settings
                for(String key : definedDevices.keySet())
                {
                    if (mixerHardwareName.contains(key))
                    {
                        mSettings = definedDevices.get(key);
                        defined = true;
                    }
                }
                // Always create mixerstream for device even if not used.
                MixerStreamHardware mixerStreamHardware = this.mixerHardwareStreams.get(mixerInfo);
                if (mixerStreamHardware == null)
                {
                    mixerStreamHardware = new MixerStreamHardware(mixerInfo, mSettings);
                    this.mixerHardwareStreams.put(mixerInfo, mixerStreamHardware);
                }
                if (mixerStreamHardware.canBeRecorded() || mixerStreamHardware.canPlayTo())
                {
                    if (!mSettings.optBoolean("disabled", false) && (!hideUndefined || defined))
                    {
                        availableMixerStreams.add(mixerStreamHardware);
                    }
                    if (!hideUndefined || defined)
                        allMixerStreams.add(mixerStreamHardware);
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        JSONArray devices = this.audioSettings.optJSONArray("devices");
        if (devices != null)
        {
            devices.forEach((dsn) -> {
                JSONObject jo = (JSONObject) dsn;
                if (jo.has("name") && jo.has("execute") && jo.optString("type", "").equals("process"))
                {
                    String mxName = jo.optString("name");
                    MixerStreamProcess msp = mixerProcessStreams.get(mxName);
                    if (msp == null)
                    {
                        msp = new MixerStreamProcess(jo);
                        mixerProcessStreams.put(mxName, msp);
                    }
                    if (!jo.optBoolean("disabled", false))
                        availableMixerStreams.add(msp);
                    allMixerStreams.add(msp);
                }
            });
        }
    }

    public JSONArray getAvailableDevices()
    {
        JSONArray ra = new JSONArray();
        Iterator<MixerStream> mixerIterator = this.availableMixerStreams.iterator();
        while(mixerIterator.hasNext())
        {
            MixerStream mixer = mixerIterator.next();
            ra.put(mixer.getMixerName());
        }
        return ra;
    }

    public JSONObject getAvailableStates()
    {
        JSONObject ro = new JSONObject();
        Iterator<MixerStream> mixerIterator = this.availableMixerStreams.iterator();
        while(mixerIterator.hasNext())
        {
            MixerStream mixer = mixerIterator.next();
            JSONObject stateObject = new JSONObject();
            stateObject.put("settings", mixer.getMixerSettings());
            stateObject.put("isAlive", mixer.isAlive());
            stateObject.put("canBeRecorded", mixer.canBeRecorded());
            stateObject.put("canPlayTo", mixer.canPlayTo());
            stateObject.put("outputStreamCount", mixer.outputStreamCount());
            ro.put(mixer.getMixerName(), stateObject);
        }
        return ro;
    }

    public JSONObject getAudioSettings()
    {
        JSONArray devices = new JSONArray();
        for(MixerStream mixerStream : this.allMixerStreams)
        {
            devices.put(mixerStream.getMixerSettings());
        }
        this.audioSettings.put("devices", devices);
        return this.audioSettings;
    }

    public void shutdown()
    {
        this.mixerHardwareStreams.forEach((k,v) -> {
            v.stop();
        });
        this.mixerProcessStreams.forEach((k,v) -> {
            v.stop();
        });
    }

    public void stopMixer(int devId)
    {
        MixerStream mixerStream = this.availableMixerStreams.get(devId);
        if (mixerStream != null)
            mixerStream.stop();
    }

    public void openRecordingDeviceAndWriteTo(int devId, HttpServletRequest request, HttpServletResponse httpServletResponse) throws IOException
    {
        httpServletResponse.setContentType("audio/mpeg");
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        final AsyncContext asyncContext = request.startAsync();
        final OutputStream out = httpServletResponse.getOutputStream();
        
        try
        {
            MixerStream mixerStream = this.availableMixerStreams.get(devId);
            if (!mixerStream.isAlive())
                mixerStream.start();
            mixerStream.addMP3TargetStream(out);
            mixerStream.addMixerStreamListener(new MixerStreamListener() {

                @Override
                public void onAudioStart(MixerStream mixerStream) {
                }

                @Override
                public void onSilence(MixerStream mixerStream) {
                }

                @Override
                public void onShutdown(MixerStream mixerStream) {
                    asyncContext.complete();
                }
                
            });
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
