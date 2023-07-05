package org.openstatic.sound;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

import org.json.JSONArray;

public class SoundSystem 
{
    ArrayList<Mixer.Info> inputMixers;
    ArrayList<Mixer.Info> outputMixers;
    HashMap<Mixer.Info, MixerStream> mixerStreams;

    public SoundSystem()
    {
        this.mixerStreams = new HashMap<Mixer.Info, MixerStream>();
        refreshMixers();
    }

    public void refreshMixers()
    {
        this.inputMixers = new ArrayList<Mixer.Info>();
        this.outputMixers = new ArrayList<Mixer.Info>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers)
        {
            Mixer m = AudioSystem.getMixer(mixerInfo);
            //System.err.println(mixerInfo.getName());
            //System.err.println(mixerInfo.getDescription());
            try
            {
                
                Line.Info[] sourceLines = m.getSourceLineInfo();
                //System.err.println ("Source Lines: " + String.valueOf(sourceLines.length));
                int slc = 0;
                for (Line.Info li : sourceLines)
                {
                    //System.err.println(li.getLineClass().toString());
                    if (li.getLineClass().toString().equals("interface javax.sound.sampled.SourceDataLine"))
                        slc++;
                }
                if (slc > 0)
                {
                    outputMixers.add(mixerInfo);
                }
                Line.Info[] targetLines = m.getTargetLineInfo();
                //System.err.println ("Target Lines: " + String.valueOf(targetLines.length));
                int tlc = 0;
                for (Line.Info li : targetLines)
                {
                    //System.err.println(li.getLineClass().toString());
                    if (li.getLineClass().toString().equals("interface javax.sound.sampled.TargetDataLine"))
                        tlc++;
                }
                if (tlc > 0)
                {
                    inputMixers.add(mixerInfo);
                }
                
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
            //System.err.println("");
        }
    }

    public JSONArray getRecordingDevices()
    {
        JSONArray ra = new JSONArray();
        Iterator<Mixer.Info> mixerIterator = this.inputMixers.iterator();
        while(mixerIterator.hasNext())
        {
            Mixer.Info mixer = mixerIterator.next();
            ra.put(mixer.getName());
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
            ra.put(mixer.getName());
        }
        return ra;
    }
    
    public synchronized MixerStream getMixerStream(Mixer.Info mixerInfo) throws LineUnavailableException
    {
        MixerStream mixerStream = this.mixerStreams.get(mixerInfo);
        if (mixerStream == null)
        {
            mixerStream = new MixerStream(mixerInfo);
            this.mixerStreams.put(mixerInfo, mixerStream);
        }
        if (!mixerStream.isAlive())
        {
            mixerStream = new MixerStream(mixerInfo);
            this.mixerStreams.put(mixerInfo, mixerStream);
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
            mixerStream.addOnDeathAction(() -> {
                asyncContext.complete();
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
