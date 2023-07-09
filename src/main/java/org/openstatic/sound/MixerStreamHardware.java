package org.openstatic.sound;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.json.JSONObject;
import org.openstatic.kiss.APIWebServer;
import org.openstatic.kiss.JavaKISSMain;

import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.sound.sampled.AudioInputStream;

public class MixerStreamHardware implements Runnable, MixerStream
{
    private TargetDataLine line;
    private AudioFormat format;
    private Mixer.Info mixerInfo;
    private Mixer mixer;
    private ArrayList<OutputStream> outputMp3;
    private ArrayList<OutputStream> outputRaw;
    private Thread myThread;
    private ArrayList<MixerStreamListener> listeners;
    private double rms;
    private boolean silence;
    private boolean longSilence;
    private long silenceStartAt;
    private JSONObject mixerSettings;
    private File recordingFile;
    private FileOutputStream recordingOutputStream;
    private long recordingStart;

    public MixerStreamHardware(Mixer.Info mixerInfo, JSONObject mixerSettings)
    {
        this.mixerSettings = mixerSettings;
        this.format = new AudioFormat(
            mixerSettings.optFloat("sampleRate", 44100),  // Sample Rate
            mixerSettings.optInt("sampleSizeInBits", 16),     // Size of SampleBits
            mixerSettings.optInt("channels", 1),      // Number of Channels
            mixerSettings.optBoolean("signed", true),   // Is Signed?
            mixerSettings.optBoolean("bigEndian", false)   // Is Big Endian?
        );
        this.silenceStartAt = System.currentTimeMillis();
        this.silence = true;
        this.longSilence = true;
        this.outputMp3 = new ArrayList<OutputStream>();
        this.outputRaw = new ArrayList<OutputStream>();
        this.listeners = new ArrayList<MixerStreamListener>();
        this.mixerInfo = mixerInfo;
        this.mixer = AudioSystem.getMixer(mixerInfo);
    }

    @Override
    public void start()
    {
        if (!this.isAlive())
        {
            try
            {
                this.format = new AudioFormat(
                    mixerSettings.optFloat("sampleRate", 44100),  // Sample Rate
                    mixerSettings.optInt("sampleSizeInBits", 16),     // Size of SampleBits
                    mixerSettings.optInt("channels", 1),      // Number of Channels
                    mixerSettings.optBoolean("signed", true),   // Is Signed?
                    mixerSettings.optBoolean("bigEndian", false)   // Is Big Endian?
                );
                this.mixer.open();
                this.line = (TargetDataLine) AudioSystem.getTargetDataLine(this.format, this.mixerInfo);
                line.open(format);
                line.start();
                this.myThread = new Thread(this);
                this.myThread.setPriority(Thread.MAX_PRIORITY);
                this.myThread.start();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    public void stop()
    {
        try
        {
            if (this.line != null)
            {
                if (this.line.isOpen())
                    this.line.close();
            }
            if (this.mixer.isOpen())
                this.mixer.close();
        } catch (Exception e) {}
        this.silence = true;
        if (!this.longSilence)
        {
            this.longSilence = true;
            this.fireLongSilence();
        }
    }

    public JSONObject getMixerSettings()
    {
        return this.mixerSettings;
    }

    @Override
    public String getMixerName()
    {
        return this.mixerSettings.optString("name", mixer.getMixerInfo().getName());
    }

    public double getRMS()
    {
        return this.rms;
    }
    
    public void addMixerStreamListener(MixerStreamListener l)
    {
        if (!this.listeners.contains(l))
        {
            this.listeners.add(l);
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

    @Override
    public int outputStreamCount()
    {
        return this.outputMp3.size() + this.outputRaw.size();
    }

    public boolean isAlive()
    {
        if (this.myThread != null)
            return this.myThread.isAlive();
        else
            return false;
    }

    public static double calcRMS(byte[] data, int numBytesRead)
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        short[] shorts = new short[data.length / 2];
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        // Save this chunk of data.
        byteArrayOutputStream.write(data, 0, numBytesRead);

        double rms = 0;
        for (int i = 0; i < shorts.length; i++) {
            double normal = shorts[i] / 32768f;
            rms += normal * normal;
        }
        rms = Math.sqrt(rms / shorts.length);
        return rms;
    }

    public void setAutoRecord(boolean v)
    {
        this.mixerSettings.put("autoRecord", v);
    }

    private void fireLongSilence()
    {
        Thread t = new Thread(() -> {
            JavaKISSMain.mainLog("[RADIO SILENCE] " + this.getMixerName());
            if (this.recordingOutputStream != null)
            {
                try
                {
                    this.recordingOutputStream.close();
                } catch (Exception e) {}
                long recordingDuration = this.silenceStartAt - this.recordingStart;
                if (recordingDuration >= this.mixerSettings.optLong("minimumRecordDuration", 500))
                {
                    if (JavaKISSMain.apiWebServer != null)
                    {
                        JSONObject recordingEvent = new JSONObject();
                        recordingEvent.put("action", "recording");
                        recordingEvent.put("name", this.recordingFile.getName());
                        recordingEvent.put("timestamp", System.currentTimeMillis());
                        recordingEvent.put("duration",recordingDuration);
                        recordingEvent.put("uri", "jaxt/api/logs/" + this.getMixerName() + "/" + this.recordingFile.getName());
                        JavaKISSMain.apiWebServer.broadcastJSONObject(recordingEvent);
                        JavaKISSMain.apiWebServer.addHistory(recordingEvent);
                    }
                } else {
                    try
                    {
                        // delete small recording
                        this.recordingFile.delete();
                    } catch (Exception dr) {}
                }
                this.recordingFile = null;
                this.recordingOutputStream = null;
                this.recordingStart = 0;
            }
        });
        t.start();
    }

    private void fireSilenceBroken()
    {
        Thread t = new Thread(() -> {
            if (mixerSettings.optBoolean("autoRecord", false) && JavaKISSMain.logsFolder != null)
            {
                File mixerFolder = new File(JavaKISSMain.logsFolder, this.getMixerName());
                if (!mixerFolder.exists())
                    mixerFolder.mkdir();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HHmmss");
                String mp3Name = simpleDateFormat.format(new Date(System.currentTimeMillis())) + ".mp3";
                this.recordingFile = new File(mixerFolder, mp3Name);
                JavaKISSMain.mainLog("[INCOMING AUDIO] " + this.getMixerName() + " - RECORDING " + this.recordingFile.getName());
                try
                {
                    this.recordingOutputStream = new FileOutputStream(this.recordingFile);
                    this.recordingStart = System.currentTimeMillis();
                } catch (Exception e) {}
            } else {
                JavaKISSMain.mainLog("[INCOMING AUDIO] " + this.getMixerName());
            }
        });
        t.start();
    }

    @Override
    public void run() 
    {
        JavaKISSMain.mainLog("[AUDIO MIXER ACTIVATED] " + this.getMixerName() + " (" + this.mixer.getMixerInfo().getName() + ")");
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
                this.rms = calcRMS(rawInputBuffer, bytesRead);
                if (this.rms < this.mixerSettings.optDouble("rmsActivation",0.05))
                {
                    if (!this.silence)
                    {
                        this.silence = true;
                        this.silenceStartAt = System.currentTimeMillis();
                    }
                } else {
                    if (this.silence)
                    {
                        this.silence = false;
                    }
                }
                if (this.silence && !this.longSilence)
                {
                    if ((System.currentTimeMillis() - this.silenceStartAt) > 5000)
                    {
                        this.longSilence = true;
                        fireLongSilence();
                    }
                } else if (!this.silence && this.longSilence) {
                    this.longSilence = false;
                    fireSilenceBroken();
                }
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
                if (this.recordingOutputStream != null)
                {
                    try
                    {
                        this.recordingOutputStream.write(mp3OutputBuffer,0,bytesWritten);
                    } catch (Exception e) {
                        this.recordingFile = null;
                        this.recordingOutputStream = null;
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
        this.listeners.forEach((l) -> l.onShutdown(this));
        JavaKISSMain.mainLog("[AUDIO MIXER DEACTIVATED] " + this.getMixerName() + " (" + this.mixer.getMixerInfo().getName() + ")");
    }

    @Override
    public void removeMixerStreamListener(MixerStreamListener l) 
    {
        if (this.listeners.contains(l))
            this.listeners.remove(l);
    }

    @Override
    public boolean canBeRecorded() {
        
        Line.Info[] targetLines = this.mixer.getTargetLineInfo();
        int tlc = 0;
        for (Line.Info li : targetLines)
        {
            if (li.getLineClass().toString().equals("interface javax.sound.sampled.TargetDataLine"))
                tlc++;
        }
        return tlc > 0;
    }

    @Override
    public boolean canPlayTo() 
    {
        Line.Info[] sourceLines = this.mixer.getSourceLineInfo();
        int slc = 0;
        for (Line.Info li : sourceLines)
        {
            if (li.getLineClass().toString().equals("interface javax.sound.sampled.SourceDataLine"))
                slc++;
        }
        return slc > 0;
    }
}
