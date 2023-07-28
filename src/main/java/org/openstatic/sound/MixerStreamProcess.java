package org.openstatic.sound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openstatic.kiss.APIWebServer;
import org.openstatic.kiss.JavaKISSMain;
import org.openstatic.sound.dtmf.DTMFUtil;
import org.tritonus.share.sampled.file.AudioOutputStream;

import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.sound.sampled.AudioInputStream;

public class MixerStreamProcess implements Runnable, MixerStream
{
    private ProcessBuilder processBuilder;
    private ProcessBuilder playExecuteProcessBuilder;
    private Process process;
    private ArrayList<OutputStream> outputMp3;
    private ArrayList<OutputStream> outputRaw;
    private Thread myThread;
    private ArrayList<MixerStreamListener> listeners;
    private double rms;
    private boolean silence;
    private boolean longSilence;
    private boolean userStop;
    private long silenceStartAt;
    private JSONObject mixerSettings;
    private File recordingFile;
    private FileOutputStream recordingOutputStream;
    private long recordingStart;
    private OutputStream processOutputStream;
    private InputStream processInputStream;
    private AudioFormat format;
    private String execString;
    private String playExecString;
    private DTMFUtil dtmfUtil;
    private char lastDTMF;
    private String dtmfSequence;
    private long lastDTMFToneAt;

    public MixerStreamProcess(JSONObject mixerSettings)
    {
        this.mixerSettings = mixerSettings;
        this.silenceStartAt = System.currentTimeMillis();
        this.silence = true;
        this.userStop = false;
        this.longSilence = true;
        this.outputMp3 = new ArrayList<OutputStream>();
        this.outputRaw = new ArrayList<OutputStream>();
        this.listeners = new ArrayList<MixerStreamListener>();
        
        rebuild();
    }

    @Override
    public float getSampleRate()
    {
        return this.format.getSampleRate();
    }

    @Override
    public int getNumChannels()
    {
        return this.format.getChannels();
    }

    public void rebuild()
    {
        this.format = new AudioFormat(
            mixerSettings.optFloat("sampleRate", 44100),  // Sample Rate
            mixerSettings.optInt("sampleSizeInBits", 16),     // Size of SampleBits
            mixerSettings.optInt("channels", 1),      // Number of Channels
            mixerSettings.optBoolean("signed", true),   // Is Signed?
            mixerSettings.optBoolean("bigEndian", false)   // Is Big Endian?
        );
        this.dtmfUtil = new DTMFUtil(this);
        this.lastDTMF = '_';
        this.dtmfSequence = "";
        if (this.mixerSettings.has("execute"))
        {
            JSONArray command = mixerSettings.optJSONArray("execute");
            final ArrayList<String> commandArray = new ArrayList<String>();
            for(int i = 0; i < command.length(); i++)
            {
                String cs = command.getString(i);
                Set<String> keySet = mixerSettings.keySet();
                for(Iterator<String> keyIterator = keySet.iterator(); keyIterator.hasNext();)
                {
                    String key = keyIterator.next();
                    cs = cs.replaceAll(Pattern.quote("{{" + key + "}}"), mixerSettings.get(key).toString());
                }
                commandArray.add(cs);
            }
            this.execString = commandArray.stream().collect(Collectors.joining(" "));
            this.processBuilder = new ProcessBuilder(commandArray);
        }

        if (this.mixerSettings.has("playExecute"))
        {
            JSONArray command = mixerSettings.optJSONArray("playExecute");
            final ArrayList<String> commandArray = new ArrayList<String>();
            for(int i = 0; i < command.length(); i++)
            {
                String cs = command.getString(i);
                Set<String> keySet = mixerSettings.keySet();
                for(Iterator<String> keyIterator = keySet.iterator(); keyIterator.hasNext();)
                {
                    String key = keyIterator.next();
                    cs = cs.replaceAll(Pattern.quote("{{" + key + "}}"), mixerSettings.get(key).toString());
                }
                commandArray.add(cs);
            }
            this.playExecString = commandArray.stream().collect(Collectors.joining(" "));
            this.playExecuteProcessBuilder = new ProcessBuilder(commandArray);
        }
    }

    @Override
    public void start()
    {
        if (!this.isAlive())
        {
            this.rebuild();
            try
            {
                this.format = new AudioFormat(
                    mixerSettings.optFloat("sampleRate", 44100),  // Sample Rate
                    mixerSettings.optInt("sampleSizeInBits", 16),     // Size of SampleBits
                    mixerSettings.optInt("channels", 1),      // Number of Channels
                    mixerSettings.optBoolean("signed", true),   // Is Signed?
                    mixerSettings.optBoolean("bigEndian", false)   // Is Big Endian?
                );
                this.process = this.processBuilder.start();
                this.processInputStream = this.process.getInputStream();
                this.processOutputStream = this.process.getOutputStream();
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
        this.userStop = true;
        if (this.processInputStream != null)
        {
            try
            {
                this.processInputStream.close();
            } catch (Exception cise) {}
        }
         if (this.processOutputStream != null)
        {
            try
            {
                this.processOutputStream.close();
            } catch (Exception cose) {}
        }
        this.silence = true;
        if (!this.longSilence)
        {
            this.longSilence = true;
            this.fireLongSilence();
        }
        if (this.process != null)
        {
            this.process.destroy();
        }
    }

    public JSONObject getMixerSettings()
    {
        return this.mixerSettings;
    }

    @Override
    public String getMixerName()
    {
        return this.mixerSettings.optString("name", "UNKNOWN");
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

    private void rmsEvents()
    {
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
            if ((System.currentTimeMillis() - this.silenceStartAt) > this.mixerSettings.optLong("silenceTimeout", 5000))
            {
                this.longSilence = true;
                fireLongSilence();
            }
        } else if (!this.silence && this.longSilence) {
            this.longSilence = false;
            fireSilenceBroken();
        }
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
                long recordingDuration = (System.currentTimeMillis() - this.recordingStart);
                
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
                this.listeners.forEach((l) -> l.onSilence(MixerStreamProcess.this));
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
                JavaKISSMain.mainLog("[INCOMING AUDIO] " + this.getMixerName() + " - RECORDING");
                try
                {
                    this.recordingOutputStream = new FileOutputStream(this.recordingFile);
                    this.recordingStart = System.currentTimeMillis();
                } catch (Exception e) {}
            } else {
                JavaKISSMain.mainLog("[INCOMING AUDIO] " + this.getMixerName());
            }
            this.listeners.forEach((l) -> l.onAudioInput(MixerStreamProcess.this));
        });
        t.start();
    }

    private void fireDTMF(final char dtmf)
    {
        Thread t = new Thread(() -> {
            this.listeners.forEach((l) -> l.onDTMF(MixerStreamProcess.this, dtmf));
        });
        t.start();
    }

    private void fireDTMFSequence(final String dtmfSequence)
    {
        Thread t = new Thread(() -> {
            this.listeners.forEach((l) -> l.onDTMFSequence(MixerStreamProcess.this, dtmfSequence));
        });
        t.start();
    }

    private void dtmfEvents(byte[] rawInputBuffer)
    {
        if (!this.longSilence)
        {
            char dtmfChar = dtmfUtil.decodeNextFrameMono(rawInputBuffer);
            if (dtmfChar != '_' && dtmfChar != this.lastDTMF)
            {
                fireDTMF(dtmfChar);
                dtmfSequence += dtmfChar;
                this.lastDTMFToneAt = System.currentTimeMillis();
            }
            this.lastDTMF = dtmfChar;
        }
        
        if ((System.currentTimeMillis() - this.lastDTMFToneAt) > mixerSettings.optLong("dtmfTimeout", 5000l) && !dtmfSequence.equals(""))
        {
            fireDTMFSequence(this.dtmfSequence);
            dtmfSequence = "";
        }
    }

    @Override
    public void run() 
    {
        this.userStop = false;
        try
        {
            this.listeners.forEach((l) -> l.onStartup(this));
        } catch (Exception xe) {}
        JavaKISSMain.mainLog("[AUDIO MIXER ACTIVATED] " + this.getMixerName() + " (" + this.execString + ")");
        AudioInputStream audioInputStream = new AudioInputStream(this.processInputStream, this.format, -1);
        try
        {
            boolean USE_VARIABLE_BITRATE = false;
            int GOOD_QUALITY_BITRATE = 128;
            LameEncoder encoder = new LameEncoder(audioInputStream.getFormat(), GOOD_QUALITY_BITRATE, MPEGMode.MONO, Lame.QUALITY_HIGHEST, USE_VARIABLE_BITRATE);

            int pcmBuffSize = 8192;
            byte[] rawInputBuffer = new byte[pcmBuffSize];
            byte[] mp3OutputBuffer = new byte[pcmBuffSize];

            int bytesRead;
            int bytesWritten;

            while(this.process.isAlive()) 
            {
                if (audioInputStream.available() > 0)
                {
                    bytesRead = audioInputStream.read(rawInputBuffer);
                    this.rms = calcRMS(rawInputBuffer, bytesRead);
                    rmsEvents();
                    if (mixerSettings.optBoolean("dtmf", false))
                    {
                        dtmfEvents(rawInputBuffer);
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
                } else {
                    this.rms = 0;
                    rmsEvents();
                    try
                    {
                        Thread.sleep(10);
                    } catch (InterruptedException iex) {}
                }
            }
            encoder.close();
        } catch (IOException e) {
            // totally fine
            //e.printStackTrace(System.err);
        } finally {
            try
            {
                this.processInputStream.close();
                this.processOutputStream.close();
            } catch (Exception e2) {}
        }
        try
        {
            this.listeners.forEach((l) -> l.onShutdown(this));
        } catch (Exception xe) {}
        JavaKISSMain.mainLog("[AUDIO MIXER DEACTIVATED] " + this.getMixerName() + " (" + this.execString + ")");
        if (!userStop && this.mixerSettings.optBoolean("autoRestart", true))
        {
            Thread x = new Thread(() -> {
                try
                {
                    Thread.sleep(2000);
                    MixerStreamProcess.this.start();
                    JavaKISSMain.mainLog("[AUDIO MIXER AUTO-RESTART] " + MixerStreamProcess.this.getMixerName() + " (" + MixerStreamProcess.this.execString + ")");
                } catch (Exception rsE) {}
            });
            x.start();
        }
    }

    @Override
    public void removeMixerStreamListener(MixerStreamListener l) 
    {
        if (this.listeners.contains(l))
            this.listeners.remove(l);
    }

    @Override
    public boolean canBeRecorded() {
        
        return this.mixerSettings.optBoolean("canBeRecorded", false);
    }

    @Override
    public boolean canPlayTo()
    {
        return this.mixerSettings.optBoolean("canPlayTo", false);
    }
    

    @Override
    public void restart() {
        Thread t = new Thread(() -> {
            try
            {
                MixerStreamProcess.this.stop();
                Thread.sleep(2000);
                MixerStreamProcess.this.start();
            } catch (Exception e) {
                
            }
        });
        t.start();
    }

    @Override
    public void setPTT(boolean v)
    {
        if (this.mixerSettings.has("ptt"))
        {
            JSONObject pttObject = this.mixerSettings.optJSONObject("ptt");
            if (pttObject != null)
            {
                if (pttObject.has("type"))
                {
                    String pttType = pttObject.optString("type", "");
                    if (pttType.equals("rts") && pttObject.has("serialPort"))
                    {
                        String serialPort = pttObject.optString("serialPort");
                        JavaKISSMain.serialSystem.setRTS(serialPort, v);
                    }
                }
            }
        }
    }

    @Override
    public void play(byte[] clipData) 
    {
        if (this.canPlayTo())
        {
            Thread t = new Thread(() -> {
                try
                {
                    JavaKISSMain.mainLog("[PLAYBACK STARTED] " + this.getMixerName());
                    ByteArrayInputStream bais = new ByteArrayInputStream(clipData);
                    // determine the format
                    AudioFormat aff = AudioSystem.getAudioFileFormat(bais).getFormat();
                    // reset the stream
                    bais.reset();
                    // create AIS in the orginal format
                    AudioInputStream fAIS = new AudioInputStream(bais, aff, AudioSystem.NOT_SPECIFIED);
                    // create AIS in the target format
                    AudioInputStream ais = AudioSystem.getAudioInputStream(this.format, fAIS);
                    this.setPTT(true);
                    if (this.playExecuteProcessBuilder != null)
                    {
                        JavaKISSMain.mainLog("[PLAYBACK EXECUTE] " + this.getMixerName() + " (" + this.playExecString + ")");
                        Process playExecProc = this.playExecuteProcessBuilder.start();
                        OutputStream pepOutputStream = playExecProc.getOutputStream();
                        ais.transferTo(pepOutputStream);
                        pepOutputStream.flush();
                        pepOutputStream.close();
                        playExecProc.waitFor();
                        JavaKISSMain.mainLog("[PLAYBACK EXECUTE TERMINATED] " + this.getMixerName() + " (" + this.playExecString + ")");
                    } else {
                        ais.transferTo(this.processOutputStream);
                    }
                    ais.close();
                    JavaKISSMain.mainLog("[PLAYBACK ENDED] " + this.getMixerName());
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
                this.setPTT(false);
            });
            t.start();
        }
    }
}
