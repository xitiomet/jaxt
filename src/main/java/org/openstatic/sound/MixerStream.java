package org.openstatic.sound;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.sound.sampled.AudioInputStream;

public class MixerStream implements Runnable
{
    private TargetDataLine line;
    private AudioFormat format;
    private Mixer mixer;
    private ArrayList<OutputStream> outputMp3;
    private ArrayList<OutputStream> outputRaw;
    private Thread myThread;
    private ArrayList<Runnable> deathWatch;

    public MixerStream(Mixer.Info mixerInfo) throws LineUnavailableException
    {
        this.format = new AudioFormat(
            44100,  // Sample Rate
            16,     // Size of SampleBits
            1,      // Number of Channels
            true,   // Is Signed?
            false   // Is Big Endian?
        );
        this.outputMp3 = new ArrayList<OutputStream>();
        this.outputRaw = new ArrayList<OutputStream>();
        this.deathWatch = new ArrayList<Runnable>();
        this.mixer = AudioSystem.getMixer(mixerInfo);        
        this.mixer.open();
        this.line = (TargetDataLine) AudioSystem.getTargetDataLine(format, mixerInfo);
        line.open(format);
        line.start();
        this.myThread = new Thread(this);
        this.myThread.setPriority(Thread.MAX_PRIORITY);
        this.myThread.start();
    }
    
    public void addOnDeathAction(Runnable r)
    {
        if (!this.deathWatch.contains(r))
        {
            this.deathWatch.add(r);
        }
    }

    public void addMP3TargetStream(OutputStream os)
    {
        if (!this.outputMp3.contains(os))
        {
            this.outputMp3.add(os);
        }
    }

    public void addRawTargetStream(OutputStream os)
    {
        if (!this.outputRaw.contains(os))
        {
            this.outputRaw.add(os);
        }
    }

    public int outputCount()
    {
        return this.outputMp3.size() + this.outputRaw.size();
    }

    public boolean isAlive()
    {
        return this.myThread.isAlive();
    }

    @Override
    public void run() 
    {
        AudioInputStream audioInputStream = new AudioInputStream(this.line);
        try
        {
            boolean USE_VARIABLE_BITRATE = false;
            int GOOD_QUALITY_BITRATE = 128;
            LameEncoder encoder = new LameEncoder(audioInputStream.getFormat(), GOOD_QUALITY_BITRATE, MPEGMode.MONO, Lame.QUALITY_HIGHEST, USE_VARIABLE_BITRATE);

            byte[] rawInputBuffer = new byte[encoder.getPCMBufferSize()];
            byte[] mp3OutputBuffer = new byte[encoder.getPCMBufferSize()];

            int bytesRead;
            int bytesWritten;

            while(0 < (bytesRead = audioInputStream.read(rawInputBuffer))) 
            {
                bytesWritten = encoder.encodeBuffer(rawInputBuffer, 0, bytesRead, mp3OutputBuffer);
                for (OutputStream outputMp3Stream : (ArrayList<OutputStream>) this.outputMp3.clone()) 
                {
                    try
                    {
                        outputMp3Stream.write(mp3OutputBuffer,0,bytesWritten);
                    } catch (Exception e) {
                        outputMp3.remove(mp3OutputBuffer);
                    }
                }
                for (OutputStream outputRawStream : (ArrayList<OutputStream>) this.outputRaw.clone()) 
                {
                    try
                    {
                        outputRawStream.write(rawInputBuffer);
                    } catch (Exception e) {
                        outputRaw.remove(rawInputBuffer);
                    }
                }
            }

            encoder.close();
        } catch (IOException e) {
            // totally fine
            //e.printStackTrace(System.err);
        } finally {
            this.line.stop();
            this.line.close();
        }
        this.deathWatch.forEach((d) -> d.run());
    }
}
