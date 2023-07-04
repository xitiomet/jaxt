package org.openstatic.sound;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.spi.AudioFileWriter;
import javax.sound.sampled.AudioInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

public class SoundSystem 
{
    ArrayList<Mixer.Info> inputMixers;
    ArrayList<Mixer.Info> outputMixers;

    public SoundSystem()
    {
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
    
    public void openRecordingDeviceAndWriteTo(int devId, HttpServletRequest request, HttpServletResponse httpServletResponse) throws IOException
    {
        httpServletResponse.setContentType("audio/mpeg");
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        final AsyncContext asyncContext = request.startAsync();
        final OutputStream out = httpServletResponse.getOutputStream();
        TargetDataLine line;
        AudioFormat format = new AudioFormat(
            44100,  // Sample Rate
            16,     // Size of SampleBits
            1,      // Number of Channels
            true,   // Is Signed?
            false   // Is Big Endian?
        );

        Mixer.Info mixerInfo = this.inputMixers.get(devId);
        Mixer mixer = AudioSystem.getMixer(mixerInfo);
        //System.err.println("Mixer Selected: " + mixerInfo.getName());
    
        try 
        {
            mixer.open();
            line = (TargetDataLine) AudioSystem.getTargetDataLine(format, mixerInfo);
            //System.err.println(line.getFormat().toString());
            line.open(format);
            // Begin audio capture.
            line.start();
            final TargetDataLine fLine = line;
            Thread runThread = new Thread(() ->
            {
                AudioInputStream audioInputStream = new AudioInputStream(fLine);
                try
                {
                    boolean USE_VARIABLE_BITRATE = false;
                    int GOOD_QUALITY_BITRATE = 128;
                    LameEncoder encoder = new LameEncoder(audioInputStream.getFormat(), GOOD_QUALITY_BITRATE, MPEGMode.MONO, Lame.QUALITY_HIGHEST, USE_VARIABLE_BITRATE);

                    byte[] inputBuffer = new byte[encoder.getPCMBufferSize()];
                    byte[] outputBuffer = new byte[encoder.getPCMBufferSize()];

                    int bytesRead;
                    int bytesWritten;

                    while(0 < (bytesRead = audioInputStream.read(inputBuffer))) {
                        bytesWritten = encoder.encodeBuffer(inputBuffer, 0, bytesRead, outputBuffer);
                        out.write(outputBuffer, 0, bytesWritten);
                    }

                    encoder.close();
                } catch (IOException e) {
                    // totally fine
                    //e.printStackTrace(System.err);
                } finally {
                    fLine.stop();
                    fLine.close();
                }
                asyncContext.complete();
            });
            runThread.setPriority(Thread.MAX_PRIORITY);
            runThread.start();
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        SoundSystem ss = new SoundSystem();
        System.err.println(ss.getRecordingDevices().toString(2));
        System.err.println(ss.getPlaybackDevices().toString(2));
    }
}
