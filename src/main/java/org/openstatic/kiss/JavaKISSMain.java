package org.openstatic.kiss;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;
import org.json.JSONObject;

public class JavaKISSMain implements AX25PacketListener, Runnable
{
    private KISSClient kClient;
    private Thread mainThread;
    private SimpleDateFormat simpleDateFormat;;

    public static File logsFolder;
    public static JSONObject settings;
    public static File settingsFile;

    public JavaKISSMain(KISSClient client)
    {
        this.kClient = client;
        String pattern = "HH:mm:ss yyyy-MM-dd";
        this.simpleDateFormat = new SimpleDateFormat(pattern);
        client.addAX25PacketListener(this);
        this.mainThread = new Thread(this);
        if (JavaKISSMain.settings.optBoolean("txTest", false))
            this.mainThread.start();
    }

    public void run()
    {
        int seq = 1;
        while(true)
        {
            try
            {
                Thread.sleep(JavaKISSMain.settings.optLong("txTestDelay", 10000));
                String tsString = String.valueOf(System.currentTimeMillis());
                String seqString = String.valueOf(seq);
                String payload = "This is a semi-long Test Transmission for the sake of testing packet radio @" + tsString + " #" + seqString;
                if (settings.has("testPayload"))
                {
                    payload = settings.optString("testPayload").replaceAll(Pattern.quote("{{ts}}"), tsString).replaceAll(Pattern.quote("{{seq}}"), tsString);
                }
                Packet packet = new Packet("XXX", "XYZ", payload);
                kClient.send(packet);

                jsonLogAppend("tx.json", packet.toJSONObject());

                if (JavaKISSMain.settings.optBoolean("verbose", false))
                {
                    System.err.println("[" + this.simpleDateFormat.format(packet.getTimestamp()) + "] (Tx) " + packet.toLogString());
                }
                seq++;
            } catch (Exception e) {
                log(e);
            }
        }
    }

    public static void main(String[] args)
    {
        CommandLine cmd = null;
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        Option testOption = new Option("t", "test", true, "Send test packets");
        testOption.setOptionalArg(true);
        options.addOption(testOption);
        options.addOption(new Option("h", "host", true, "Specify TNC host (Default: 127.0.0.1)"));
        options.addOption(new Option("p", "port", true, "KISS Port (Default: 8100)"));
        options.addOption(new Option("f", "config-file", true, "Specify config file (.json)"));
        options.addOption(new Option("v", "verbose", false, "Shows Packets"));
        options.addOption(new Option("?", "help", false, "Shows help"));

        JavaKISSMain.settingsFile = new File(System.getProperty("user.home"),".java-kiss-settings.json");
        JavaKISSMain.settings = loadJSONObject(settingsFile);
        try
        {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("?"))
            {
                showHelp(options);
            }

            if (cmd.hasOption("f"))
            {
                JavaKISSMain.settingsFile = new File(cmd.getOptionValue("f"));
                JavaKISSMain.settings = loadJSONObject(settingsFile);
            }

            if (cmd.hasOption("t"))
            {
                settings.put("txTest", true);
                settings.put("txTestDelay", Long.valueOf(cmd.getOptionValue("t", "10")).longValue() * 1000l);
            }

            if (cmd.hasOption("v"))
            {
                settings.put("verbose", true);
            }

            if (cmd.hasOption("h"))
            {
                settings.put("host", cmd.getOptionValue("h"));
            }

            if (cmd.hasOption("p"))
            {
                settings.put("port", Integer.valueOf(cmd.getOptionValue("p")).intValue());
            }
            JavaKISSMain.logsFolder = new File(JavaKISSMain.settings.optString("logPath", "./java-kiss-logs"));
            if (!JavaKISSMain.logsFolder.exists())
            {
                JavaKISSMain.logsFolder.mkdirs();
            }
            KISSClient kClient = new KISSClient(settings.optString("host"), settings.optInt("port",8100));
            JavaKISSMain jkm = new JavaKISSMain(kClient);
            Runtime.getRuntime().addShutdownHook(new Thread() 
            { 
                public void run() 
                { 
                    saveSettings();
                }
            });
            kClient.connect();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public static void showHelp(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "java-kiss", "Java KISS: A Java KISS TNC Client implementation", options, "" );
        System.exit(0);
    }

    @Override
    public void onReceived(Packet packet)
    {
        JSONObject logEntry = packet.toJSONObject();
        jsonLogAppend("rx.json", logEntry);
        jsonLogAppend(packet.getSourceCallsign() + ".json", logEntry);
        if (settings.optBoolean("verbose", false))
        {
            System.err.println("[" + this.simpleDateFormat.format(packet.getTimestamp()) + "] (Rx) " + packet.toLogString());
        }
    }

    public static synchronized void logAppend(String filename, String text)
    {
        try
        {
            String pattern = "HH:mm:ss yyyy-MM-dd";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            File logFile = new File(JavaKISSMain.logsFolder, filename);
            File logFileParent = logFile.getParentFile();
            if (!logFileParent.exists())
                logFileParent.mkdirs();
            FileOutputStream logOutputStream = new FileOutputStream(logFile, true);;
            PrintWriter logWriter = new PrintWriter(logOutputStream, true, Charset.forName("UTF-8"));
            String logText = "[" + simpleDateFormat.format(new Date(System.currentTimeMillis())) + "] " + text;
            logWriter.println(logText);
            logWriter.flush();
            logWriter.close();
            logOutputStream.close();
            if (JavaKISSMain.settings.optBoolean("verbose", false))
                System.err.println(logText);
        } catch (Exception e) {

        }
    }

    public static synchronized void jsonLogAppend(String filename, JSONObject object)
    {
        try
        {
            Date now = new Date(object.optLong("timestamp", (new Date(System.currentTimeMillis())).getTime()));
            String pattern = "yyyy-MM-dd HH:mm:ss";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            File logFile = new File(JavaKISSMain.logsFolder, filename);
            File logFileParent = logFile.getParentFile();
            if (!logFileParent.exists())
                logFileParent.mkdirs();
            FileOutputStream logOutputStream = new FileOutputStream(logFile, true);;
            PrintWriter logWriter = new PrintWriter(logOutputStream, true, Charset.forName("UTF-8"));
            object.put("localTime", simpleDateFormat.format(now));
            logWriter.println(object.toString());
            logWriter.flush();
            logWriter.close();
            logOutputStream.close();
        } catch (Exception e) {
            log(e);
        }
    }

    public static void log(Exception e)
    {
        try
        {
            String msg = e.getMessage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true, Charset.forName("UTF-8"));
            e.printStackTrace(ps);
            ps.flush();

            String pattern = "HH:mm:ss yyyy-MM-dd";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            File logFile = new File(JavaKISSMain.logsFolder, "exceptions.log");
            File logFileParent = logFile.getParentFile();
            if (!logFileParent.exists())
                logFileParent.mkdirs();
            FileOutputStream logOutputStream = new FileOutputStream(logFile, true);;
            PrintWriter logWriter = new PrintWriter(logOutputStream, true, Charset.forName("UTF-8"));
            String logText = "[" + simpleDateFormat.format(new Date(System.currentTimeMillis())) + "] " + msg + "\n" + baos.toString();
            logWriter.println(logText);
            logWriter.flush();
            logWriter.close();
            logOutputStream.close();
        } catch (Exception e2) {
        }
    }

    public static void saveSettings()
    {
        if (JavaKISSMain.settings.has("txTest"))
            JavaKISSMain.settings.remove("txTest");
        saveJSONObject(JavaKISSMain.settingsFile, JavaKISSMain.settings);
    }

    public static JSONObject loadJSONObject(File file)
    {
        try
        {
            FileInputStream fis = new FileInputStream(file);
            StringBuilder builder = new StringBuilder();
            int ch;
            while((ch = fis.read()) != -1)
            {
                builder.append((char)ch);
            }
            fis.close();
            JSONObject props = new JSONObject(builder.toString());
            return props;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void saveJSONObject(File file, JSONObject obj)
    {
        try
        {
            FileOutputStream fos = new FileOutputStream(file);
            PrintStream ps = new PrintStream(fos);
            ps.print(obj.toString(2));
            ps.close();
            fos.close();
        } catch (Exception e) {
        }
    }

    @Override
    public void onKISSConnect(InetSocketAddress host)
    {
        JavaKISSMain.logAppend("main.log", "[KISS Connected] " + host.toString());
    }

    @Override
    public void onKISSDisconnect(InetSocketAddress host)
    {
        JavaKISSMain.logAppend("main.log", "[KISS Disconnected] " + host.toString());
    }
}
