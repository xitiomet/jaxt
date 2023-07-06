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
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openstatic.kiss.JavaKISSMain;

public class SoundSystem 
{
    ArrayList<Mixer.Info> inputMixers;
    ArrayList<Mixer.Info> outputMixers;
    HashMap<Mixer.Info, MixerStream> mixerStreams;
    HashMap<Mixer.Info, String> mixerNames;
    HashMap<String, Mixer.Info> mixerKeys;
    HashMap<Mixer.Info, JSONObject> mixerSettings;

    JSONObject audioSettings;

    public SoundSystem()
    {
        this.audioSettings = JavaKISSMain.settings.optJSONObject("audio", new JSONObject());
        this.mixerStreams = new HashMap<Mixer.Info, MixerStream>();
        this.mixerNames = new HashMap<Mixer.Info, String>();
        refreshMixers();
    }

    public void refreshMixers()
    {
        this.inputMixers = new ArrayList<Mixer.Info>();
        this.outputMixers = new ArrayList<Mixer.Info>();
        this.mixerNames = new HashMap<Mixer.Info, String>();
        this.mixerKeys = new HashMap<String, Mixer.Info>();
        this.mixerSettings = new HashMap<Mixer.Info, JSONObject>();

        boolean hideUndefined = this.audioSettings.optBoolean("hideUndefined", false);
        HashMap<String, JSONObject> definedDevices = new HashMap<String, JSONObject>();
        if (this.audioSettings.has("devices"))
        {
            JSONObject devices = this.audioSettings.getJSONObject("devices");
            Set<String> devSearchNames = devices.keySet();
            devSearchNames.forEach((dsn) -> {
                definedDevices.put(dsn, devices.getJSONObject(dsn));
            });
        }
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers)
        {
            Mixer m = AudioSystem.getMixer(mixerInfo);
            try
            {
                
                Line.Info[] sourceLines = m.getSourceLineInfo();
                int slc = 0;
                for (Line.Info li : sourceLines)
                {
                    if (li.getLineClass().toString().equals("interface javax.sound.sampled.SourceDataLine"))
                        slc++;
                }
                if (slc > 0)
                {
                    String mixerName = mixerInfo.getName();
                    JSONObject mSettings = new JSONObject();
                    boolean defined = false;
                    for(String key : definedDevices.keySet())
                    {
                        if (mixerName.contains(key))
                        {
                            mSettings = definedDevices.get(key);
                            mixerKeys.put(key, mixerInfo);
                            defined = true;
                        }
                    }
                    if (!mSettings.optBoolean("disabled", false) && (!hideUndefined || defined))
                    {
                        mixerNames.put(mixerInfo, mSettings.optString("rename", mixerName));
                        mixerSettings.put(mixerInfo, mSettings);
                        outputMixers.add(mixerInfo);
                    }
                }
                Line.Info[] targetLines = m.getTargetLineInfo();
                int tlc = 0;
                for (Line.Info li : targetLines)
                {
                    if (li.getLineClass().toString().equals("interface javax.sound.sampled.TargetDataLine"))
                        tlc++;
                }
                if (tlc > 0)
                {
                    String mixerName = mixerInfo.getName();
                    JSONObject mSettings = new JSONObject();
                    boolean defined = false;
                    for(String key : definedDevices.keySet())
                    {
                        if (mixerName.contains(key))
                        {
                            mSettings = definedDevices.get(key);
                            mixerKeys.put(key, mixerInfo);
                            defined = true;
                        }
                    }
                    if (!mSettings.optBoolean("disabled", false) && (!hideUndefined || defined))
                    {
                        mixerNames.put(mixerInfo, mSettings.optString("rename", mixerName));
                        mixerSettings.put(mixerInfo, mSettings);
                        inputMixers.add(mixerInfo);
                    }
                    if (mSettings.optBoolean("watch", false) || mSettings.optBoolean("autoRecord", false))
                    {
                        getMixerStream(mixerInfo);
                    }
                }
                
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    public JSONArray getRecordingDevices()
    {
        JSONArray ra = new JSONArray();
        Iterator<Mixer.Info> mixerIterator = this.inputMixers.iterator();
        while(mixerIterator.hasNext())
        {
            Mixer.Info mixer = mixerIterator.next();
            ra.put(mixerNames.getOrDefault(mixer, mixer.getName()));
        }
        return ra;
    }

    public JSONArray getPlaybackDevices()
    {
        JSONArray ra = new JSONArray();
        Iterator<Mixer.Info> mixerIterator = this.outputMixers.iterator();
        while(mixerIterator.hasNext())
        {
            Mixer.Info mixer = mixerIterator.next();
            ra.put(mixerNames.getOrDefault(mixer, mixer.getName()));
        }
        return ra;
    }

    public JSONObject getActiveRecordingDevices()
    {
        JSONObject ro = new JSONObject();
        Iterator<Mixer.Info> mixerIterator = this.mixerStreams.keySet().iterator();
        while(mixerIterator.hasNext())
        {
            Mixer.Info mixer = mixerIterator.next();
            if (this.inputMixers.contains(mixer))
                ro.put(mixerNames.getOrDefault(mixer, mixer.getName()), this.mixerStreams.get(mixer).getMixerSettings());
        }
        return ro;
    }

    public JSONObject getActivePlaybackDevices()
    {
        JSONObject ro = new JSONObject();
        Iterator<Mixer.Info> mixerIterator = this.mixerStreams.keySet().iterator();
        while(mixerIterator.hasNext())
        {
            Mixer.Info mixer = mixerIterator.next();
            if (this.outputMixers.contains(mixer))
                ro.put(mixerNames.getOrDefault(mixer, mixer.getName()), this.mixerStreams.get(mixer).getMixerSettings());
        }
        return ro;
    }

    public JSONObject getAudioSettings()
    {
        JSONObject devices = new JSONObject();
        for(String mixerKey : this.mixerKeys.keySet())
        {
            Mixer.Info mixerInfo = this.mixerKeys.get(mixerKey);
            if (!mixerStreams.containsKey(mixerKey))
                devices.put(mixerKey,mixerSettings.get(mixerInfo));
            else
                devices.put(mixerKey,mixerStreams.get(mixerInfo).getMixerSettings());
        }
        this.audioSettings.put("devices", devices);
        return this.audioSettings;
    }

    
    public synchronized MixerStream getMixerStream(Mixer.Info mixerInfo) throws LineUnavailableException
    {
        MixerStream mixerStream = this.mixerStreams.get(mixerInfo);
        if (mixerStream == null)
        {
            mixerStream = new MixerStream(mixerInfo, mixerSettings.get(mixerInfo));
            this.mixerStreams.put(mixerInfo, mixerStream);
        }
        if (!mixerStream.isAlive())
        {
            mixerStream = new MixerStream(mixerInfo, mixerSettings.get(mixerInfo));
            this.mixerStreams.put(mixerInfo, mixerStream);
        }
        if (!mixerKeys.containsValue(mixerInfo))
        {
            mixerKeys.put(mixerInfo.getName(), mixerInfo);
        }
        return mixerStream;
    }

    public void openRecordingDeviceAndWriteTo(int devId, HttpServletRequest request, HttpServletResponse httpServletResponse) throws IOException
    {
        httpServletResponse.setContentType("audio/mpeg");
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        final AsyncContext asyncContext = request.startAsync();
        final OutputStream out = httpServletResponse.getOutputStream();
        
        try
        {
            Mixer.Info mixerInfo = this.inputMixers.get(devId);
            MixerStream mixerStream = getMixerStream(mixerInfo);
            mixerStream.addMP3TargetStream(out);
            mixerStream.addListener(new MixerStreamListener() {

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

    public static void main(String[] args)
    {
        SoundSystem ss = new SoundSystem();
        System.err.println(ss.getRecordingDevices().toString(2));
        System.err.println(ss.getPlaybackDevices().toString(2));
    }
}
