package org.openstatic.sound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.json.JSONArray;
import org.json.JSONObject;

import org.openstatic.kiss.JavaKISSMain;
import org.openstatic.sound.dtmf.DTMFUtil;

import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.sound.sampled.AudioInputStream;

public class MixerStreamHardware implements Runnable, MixerStream
{
    private TargetDataLine targetLine;
    private SourceDataLine sourceLine;
    private SourceDataLineOutputStream sourceDataLineOutputStream;
    private AudioFormat format;
    private Mixer.Info mixerInfo;
    private Mixer mixer;
    private ProcessBuilder stopExecuteProcessBuilder;
    private ProcessBuilder startExecuteProcessBuilder;
    private ProcessBuilder pttExecuteProcessBuilder;
    private ArrayList<OutputStream> outputMp3;
    private ArrayList<OutputStream> outputRaw;
    private ArrayList<MixerStream> outputMixerStreams;
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
    private DTMFUtil dtmfUtil;
    private char lastDTMF;
    private String dtmfSequence;
    private String stopExecString;
    private String startExecString;
    private String pttExecString;
    private long lastDTMFToneAt;
    private boolean ptt;

    public MixerStreamHardware(Mixer.Info mixerInfo, JSONObject mixerSettings)
    {
        this.mixerSettings = mixerSettings;
        this.silenceStartAt = System.currentTimeMillis();
        this.silence = true;
        this.ptt = false;
        this.longSilence = true;
        this.outputMp3 = new ArrayList<OutputStream>();
        this.outputRaw = new ArrayList<OutputStream>();
        this.outputMixerStreams = new ArrayList<MixerStream>();
        this.listeners = new ArrayList<MixerStreamListener>();
        this.mixerInfo = mixerInfo;
        this.mixer = AudioSystem.getMixer(mixerInfo);
        this.rebuild();
    }

    
    public String frequencyLong(String frequency)
    {
        if (frequency.endsWith("M"))
        {
            BigDecimal v = new BigDecimal(frequency.substring(0, frequency.length() - 1));
            BigDecimal million = new BigDecimal("1000000");
            String rv = v.multiply(million).toPlainString();
            if (rv.endsWith(".00"))
            rv = rv.substring(0, rv.length() - 3);
            return rv;
        } else if (frequency.endsWith("K")) {
            BigDecimal v = new BigDecimal(frequency.substring(0, frequency.length() - 1));
            BigDecimal thousand = new BigDecimal("1000");
            String rv = v.multiply(thousand).toPlainString();
            if (rv.endsWith(".00"))
                rv = rv.substring(0, rv.length() - 3);
            return rv;
        } else {
            return frequency;
        }
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
        

        if (this.mixerSettings.has("stopExecute"))
        {
            JSONArray command = mixerSettings.optJSONArray("stopExecute");
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
                if (mixerSettings.has("frequency"))
                    cs = cs.replaceAll(Pattern.quote("{{frequency.long}}"), frequencyLong(mixerSettings.get("frequency").toString()));
                commandArray.add(cs);
            }
            this.stopExecString = commandArray.stream().collect(Collectors.joining(" "));
            this.stopExecuteProcessBuilder = new ProcessBuilder(commandArray);
        } else {
            this.stopExecuteProcessBuilder = null;
            this.stopExecString = null;
        }

        if (this.mixerSettings.has("startExecute"))
        {
            JSONArray command = mixerSettings.optJSONArray("startExecute");
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
                if (mixerSettings.has("frequency"))
                    cs = cs.replaceAll(Pattern.quote("{{frequency.long}}"), frequencyLong(mixerSettings.get("frequency").toString()));
                commandArray.add(cs);
            }
            this.startExecString = commandArray.stream().collect(Collectors.joining(" "));
            this.startExecuteProcessBuilder = new ProcessBuilder(commandArray);
        } else {
            this.startExecuteProcessBuilder = null;
            this.startExecString = null;
        }

        if (this.mixerSettings.has("pttExecute"))
        {
            JSONArray command = mixerSettings.optJSONArray("pttExecute");
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
                if (mixerSettings.has("frequency"))
                    cs = cs.replaceAll(Pattern.quote("{{frequency.long}}"), frequencyLong(mixerSettings.get("frequency").toString()));
                commandArray.add(cs);
            }
            this.pttExecString = commandArray.stream().collect(Collectors.joining(" "));
            this.pttExecuteProcessBuilder = new ProcessBuilder(commandArray);
        } else {
            this.pttExecuteProcessBuilder = null;
            this.pttExecString = null;
        }
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


    @Override
    public void start()
    {
        if (!this.isAlive())
        {
            try
            {
                this.rebuild();
                if (this.startExecuteProcessBuilder != null)
                {
                    try
                    {
                        JavaKISSMain.mainLog("[START EXECUTE] " + this.getMixerName() + " (" + this.startExecString + ")");
                        Process startExecProc = this.startExecuteProcessBuilder.start();
                        startExecProc.waitFor();
                        JavaKISSMain.mainLog("[START EXECUTE TERMINATED] " + this.getMixerName() + " (" + this.startExecString + ")");
                    } catch (Exception e) {

                    }
                }
                
                this.dtmfUtil = new DTMFUtil(this);
                this.mixer.open();
                this.targetLine = (TargetDataLine) AudioSystem.getTargetDataLine(this.format, this.mixerInfo);
                targetLine.open(format);
                targetLine.start();
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
            if (this.targetLine != null)
            {
                if (this.targetLine.isOpen())
                    this.targetLine.close();
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
        if (this.stopExecuteProcessBuilder != null)
        {
            Thread t = new Thread(()-> {
                try
                {
                    Thread.sleep(1000);
                    JavaKISSMain.mainLog("[STOP EXECUTE] " + this.getMixerName() + " (" + this.stopExecString + ")");
                    Process stopExecProc = this.stopExecuteProcessBuilder.start();
                    stopExecProc.waitFor();
                    JavaKISSMain.mainLog("[STOP EXECUTE TERMINATED] " + this.getMixerName() + " (" + this.stopExecString + ")");
                } catch (Exception e) {

                }
            });
            t.start();
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

    public void removeRawTargetStream(OutputStream os)
    {
        if (this.outputRaw.contains(os))
        {
            this.outputRaw.remove(os);
        }
    }

    @Override
    public void addTargetMixerStream(MixerStream ms) 
    {
        if (!this.outputMixerStreams.contains(ms))
        {
            this.outputMixerStreams.add(ms);
        }
    }

    @Override
    public void removeTargetMixerStream(MixerStream ms)
    {
        if (this.outputMixerStreams.contains(ms))
        {
            this.outputMixerStreams.remove(ms);
        }
    }

    @Override
    public Collection<MixerStream> getTargetMixerStreams()
    {
        return this.outputMixerStreams;
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
        if (mixerSettings.optBoolean("logAudio", true))
            JavaKISSMain.mainLog("[RADIO SILENCE] " + this.getMixerName());
        this.outputMixerStreams.forEach((ms) -> {
            ms.setPTT(false);
        });
        Thread t = new Thread(() -> {
            if (this.recordingOutputStream != null)
            {
                try
                {
                    this.recordingOutputStream.close();
                } catch (Exception e) {}
            }
            final long recordingDuration = this.silenceStartAt - this.recordingStart;
            if (this.recordingFile != null)
            {
                if (recordingDuration >= this.mixerSettings.optLong("minimumRecordDuration", 500))
                {
                    this.listeners.forEach((l) -> l.onRecording(MixerStreamHardware.this, recordingDuration, this.recordingFile));
                } else {
                    try
                    {
                        // delete small recording
                        this.recordingFile.delete();
                    } catch (Exception dr) {}
                }
                this.recordingFile = null;
            }
            this.recordingOutputStream = null;
            this.recordingStart = 0;
            this.listeners.forEach((l) -> l.onSilence(MixerStreamHardware.this));
        });
        t.start();
    }

    private void fireSilenceBroken()
    {
        if (mixerSettings.optBoolean("logAudio", true))
            JavaKISSMain.mainLog("[INCOMING AUDIO] " + this.getMixerName());
        this.outputMixerStreams.forEach((ms) -> {
            ms.setPTT(true);
        });
        Thread t = new Thread(() -> {
            if (mixerSettings.optBoolean("autoRecord", false) && JavaKISSMain.logsFolder != null)
            {
                Date nowDate = new Date(System.currentTimeMillis());
                File mixerFolder = new File(JavaKISSMain.logsFolder, this.getMixerName());
                if (!mixerFolder.exists())
                    mixerFolder.mkdir();
                SimpleDateFormat simpleDateStringFormat = new SimpleDateFormat("yyyy-MM-dd");
                String dateString = simpleDateStringFormat.format(nowDate);
                File dateFolder = new File(mixerFolder, dateString);
                if (!dateFolder.exists())
                    dateFolder.mkdir();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HHmmss");
                String mp3Name = (mixerSettings.has("frequency") ? mixerSettings.optString("frequency") + " - " : "") + simpleDateFormat.format(nowDate) + ".mp3";
                this.recordingFile = new File(dateFolder, mp3Name);
                try
                {
                    this.recordingOutputStream = new FileOutputStream(this.recordingFile);
                    this.recordingStart = System.currentTimeMillis();
                } catch (Exception e) {}
            }
            this.listeners.forEach((l) -> l.onAudioInput(MixerStreamHardware.this));
        });
        t.start();
    }

    private void fireDTMF(final char dtmf)
    {
        Thread t = new Thread(() -> {
            this.listeners.forEach((l) -> l.onDTMF(MixerStreamHardware.this, dtmf));
        });
        t.start();
    }

    private void fireDTMFSequence(final String dtmfSequence)
    {
        Thread t = new Thread(() -> {
            this.listeners.forEach((l) -> l.onDTMFSequence(MixerStreamHardware.this, dtmfSequence));
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
        try
        {
            this.listeners.forEach((l) -> l.onStartup(this));
        } catch (Exception xe) {}
        JavaKISSMain.mainLog("[AUDIO MIXER ACTIVATED] " + this.getMixerName() + " (" + this.mixer.getMixerInfo().getName() + ")");
        AudioInputStream audioInputStream = new AudioInputStream(this.targetLine);
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

            while(0 < (bytesRead = audioInputStream.read(rawInputBuffer))) 
            {
                this.rms = calcRMS(rawInputBuffer, bytesRead);
                if (this.ptt) this.rms = 0.0f;
                this.rmsEvents();
                if (!this.ptt)
                {
                    if (mixerSettings.optBoolean("dtmf", false))
                    {
                        this.dtmfEvents(rawInputBuffer);
                    }
                    bytesWritten = encoder.encodeBuffer(rawInputBuffer, 0, bytesRead, mp3OutputBuffer);
                    for (OutputStream outputMp3Stream : (ArrayList<OutputStream>) this.outputMp3.clone()) 
                    {
                        try
                        {
                            outputMp3Stream.write(mp3OutputBuffer,0,bytesWritten);
                        } catch (Exception e) {
                            outputMp3.remove(outputMp3Stream);
                        }
                    }
                    if (this.recordingOutputStream != null)
                    {
                        try
                        {
                            this.recordingOutputStream.write(mp3OutputBuffer,0,bytesWritten);
                        } catch (Exception e) {
                            this.recordingOutputStream = null;
                        }
                    }
                    for (OutputStream outputRawStream : (ArrayList<OutputStream>) this.outputRaw.clone()) 
                    {
                        try
                        {
                            outputRawStream.write(rawInputBuffer);
                        } catch (Exception e) {
                            e.printStackTrace(System.err);
                            outputRaw.remove(outputRawStream);
                        }
                    }
                    if (!this.longSilence)
                    {
                        for (MixerStream outputMixerStream : (ArrayList<MixerStream>) this.outputMixerStreams.clone()) 
                        {
                            try
                            {
                                outputMixerStream.getOutputStream().write(rawInputBuffer);
                            } catch (Exception e) {
                                e.printStackTrace(System.err);
                                outputMixerStreams.remove(outputMixerStream);
                            }
                        }
                    }
                }
                try
                {
                    Thread.sleep(10);
                } catch (Exception e) {}
            }

            encoder.close();
        } catch (IOException e) {
            // totally fine
            //e.printStackTrace(System.err);
        } finally {
            this.targetLine.stop();
            this.targetLine.close();
        }
        ((ArrayList<MixerStreamListener>) this.listeners.clone()).forEach((l) -> l.onShutdown(this));
        JavaKISSMain.mainLog("[AUDIO MIXER DEACTIVATED] " + this.getMixerName() + " (" + this.mixer.getMixerInfo().getName() + ")");
    }

    @Override
    public void removeMixerStreamListener(MixerStreamListener l) 
    {
        if (this.listeners.contains(l))
            this.listeners.remove(l);
    }

    @Override
    public boolean canBeRecorded() 
    {
        if (!this.mixerSettings.has("canBeRecorded"))
        {
            Line.Info[] targetLines = this.mixer.getTargetLineInfo();
            int tlc = 0;
            for (Line.Info li : targetLines)
            {
                if (li.getLineClass().toString().equals("interface javax.sound.sampled.TargetDataLine"))
                    tlc++;
            }
            return tlc > 0;
        } else {
            return this.mixerSettings.optBoolean("canBeRecorded", false);
        }
    }

    @Override
    public boolean canPlayTo() 
    {
        if (!this.mixerSettings.has("canPlayTo"))
        {
            Line.Info[] sourceLines = this.mixer.getSourceLineInfo();
            int slc = 0;
            for (Line.Info li : sourceLines)
            {
                if (li.getLineClass().toString().equals("interface javax.sound.sampled.SourceDataLine"))
                    slc++;
            }
            return slc > 0;
        } else {
            return this.mixerSettings.optBoolean("canPlayTo", false);
        }
    }

    @Override
    public void restart() {
        Thread t = new Thread(() -> {
            try
            {
                MixerStreamHardware.this.stop();
                Thread.sleep(2000);
                MixerStreamHardware.this.start();
            } catch (Exception e) {

            }
        });
        t.start();
    }

    @Override
    public void setPTT(boolean v)
    {
        this.ptt = v;
        if (v)
        {
            JavaKISSMain.mainLog("[PTT PRESSED] " + this.getMixerName());
            if (this.pttExecuteProcessBuilder != null)
            {
                try
                {
                    JavaKISSMain.mainLog("[PTT EXECUTE] " + this.getMixerName() + " (" + this.pttExecString + ")");
                    Process pttProcess = this.pttExecuteProcessBuilder.start();
                    pttProcess.waitFor();
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
        } else {
            JavaKISSMain.mainLog("[PTT RELEASED] " + this.getMixerName());
        }
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
                    if (pttType.equals("dtr") && pttObject.has("serialPort"))
                    {
                        String serialPort = pttObject.optString("serialPort");
                        JavaKISSMain.serialSystem.setDTR(serialPort, v);
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

                    ais.transferTo(this.getOutputStream());
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

    @Override
    public OutputStream getOutputStream() 
    {
        boolean rebuildSourceLine = false;
        if (this.sourceLine == null)
        {
            rebuildSourceLine = true;
        } else if (!this.sourceLine.isRunning()) {
            rebuildSourceLine = true;
        }

        if (rebuildSourceLine)
        {
            System.err.println("Rebuilding source line");
            try
            {
                this.sourceLine = AudioSystem.getSourceDataLine(this.format, this.mixerInfo);
                sourceLine.open();
                sourceLine.start();
                FloatControl gainControl = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(1.0f);
            } catch (Exception e) {
                e.printStackTrace(System.err);
                this.sourceLine = null;
            }
        }

        if (this.sourceLine != null)
        {
            if (this.sourceDataLineOutputStream == null)
            {
                this.sourceDataLineOutputStream = new SourceDataLineOutputStream(this.sourceLine);
            } else if (!this.sourceDataLineOutputStream.isAlive()) {
                this.sourceDataLineOutputStream = new SourceDataLineOutputStream(this.sourceLine);
            }
        }
        
        return this.sourceDataLineOutputStream;
    }
}
