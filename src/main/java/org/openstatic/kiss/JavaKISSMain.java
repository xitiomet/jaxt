package org.openstatic.kiss;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openstatic.sound.SoundSystem;

public class JavaKISSMain implements AX25PacketListener, Runnable
{
    private KISSClient kClient;
    private Thread mainThread;
    private SimpleDateFormat simpleDateFormat;

    public static File logsFolder;
    public static JSONObject settings;
    public static File settingsFile;
    public static APIWebServer apiWebServer;
    public static SoundSystem soundSystem;

    public JavaKISSMain(KISSClient client)
    {
        this.kClient = client;
        this.kClient.setKissPing(true);
        String pattern = "HH:mm:ss yyyy-MM-dd";
        this.simpleDateFormat = new SimpleDateFormat(pattern);
        client.addAX25PacketListener(this);
        this.mainThread = new Thread(this);
        if (JavaKISSMain.settings.optBoolean("txTest", false))
            this.mainThread.start();
        if (JavaKISSMain.settings.has("apiPort"))
        {
            JavaKISSMain.apiWebServer = new APIWebServer(client);
        }
    }

    public void run()
    {
        while(true)
        {
            try
            {
                Thread.sleep(JavaKISSMain.settings.optLong("txTestInterval", 10000));
                String payload = settings.optString("payload","JAXT Test Transmission @{{ts}} #{{seq}}");
                AX25Packet packet = AX25Packet.buildPacket(settings.optString("source", "NOCALL1"), settings.optString("destination", "NOCALL2"), payload);
                kClient.send(packet);
            } catch (Exception e) {
                log(e);
            }
        }
    }

    public static void main(String[] args)
    {
        org.eclipse.jetty.util.log.Log.setLog(new NoLogging());
        //System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
        //org.eclipse.jetty.util.log.Log.getProperties().setProperty("org.eclipse.jetty.util.log.announce", "false");

        CommandLine cmd = null;
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        Option testOption = new Option("t", "test", true, "Send test packets (optional parameter interval in seconds, default is 10 seconds)");
        testOption.setOptionalArg(true);
        options.addOption(testOption);
        Option linkOption = new Option("z", "terminal-link", true, "Listen for a terminal call, first argument is callsign, and second is command with parameters seperated by commas (Example: -z MYCALL-1 cmd.exe,/Q)");
        linkOption.setArgs(2);
        options.addOption(linkOption);
        options.addOption(new Option("h", "host", true, "Specify TNC host (Default: 127.0.0.1)"));
        options.addOption(new Option("p", "port", true, "KISS Port (Default: 8100)"));
        Option apiOption = new Option("a", "api", true, "Enable API Web Server, and specify port (Default: 8101)");
        apiOption.setOptionalArg(true);
        options.addOption(apiOption);
        options.addOption(new Option("f", "config-file", true, "Specify config file (.json)"));
        Option loggingOption = new Option("l", "logs", true, "Enable Logging, and optionally specify a directory");
        loggingOption.setOptionalArg(true);
        options.addOption(loggingOption);
        options.addOption(new Option("s", "source", true, "Set the default source callsign."));
        options.addOption(new Option("d", "destination", true, "Destination callsign (for test payload)"));
        options.addOption(new Option("c", "commads", true, "Specify commands.json file location for web terminal"));
        options.addOption(new Option("m", "payload", true, "Payload string to send on test interval. {{ts}} for timestamp, {{seq}} for sequence."));
        options.addOption(new Option("v", "verbose", false, "Shows Packets"));
        options.addOption(new Option("x", "post", true, "HTTP POST packets received as JSON to url"));
        options.addOption(new Option("?", "help", false, "Shows help"));

        JavaKISSMain.settings = new JSONObject();
        JavaKISSMain.settingsFile = null;
        File homeSettings = new File(System.getProperty("user.home"),".jaxt.json");
        if (homeSettings.exists())
        {
            JavaKISSMain.settingsFile = homeSettings;
            JavaKISSMain.settings = loadJSONObject(homeSettings);
        }
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
                settings.put("txTestInterval", Long.valueOf(cmd.getOptionValue("t", "10")).longValue() * 1000l);
            }

            if (cmd.hasOption("a"))
            {
                settings.put("apiPort", Integer.valueOf(cmd.getOptionValue("a", "8101")).intValue());
            }

            if (cmd.hasOption("m"))
            {
                settings.put("payload", cmd.getOptionValue("m"));
            }

            if (cmd.hasOption("v"))
            {
                settings.put("verbose", true);
            }

            if (cmd.hasOption("h"))
            {
                settings.put("host", cmd.getOptionValue("h"));
            }

            if (cmd.hasOption("x"))
            {
                settings.put("postUrl", cmd.getOptionValue("x"));
            }

            if (cmd.hasOption("s"))
            {
                settings.put("source", cmd.getOptionValue("s").toUpperCase());
            }
            
            if (cmd.hasOption("z"))
            {
                JSONObject terminal = settings.optJSONObject("terminal", new JSONObject());
                String[] values = cmd.getOptionValues("z");
                JSONObject xarg = new JSONObject();
                xarg.put("type", "process");
                xarg.put("execute", new JSONArray(values[1].split(Pattern.quote(","))));
                terminal.put(values[0].toUpperCase(), xarg);
                settings.put("terminal", terminal);
            }

            if (cmd.hasOption("d"))
            {
                settings.put("destination", cmd.getOptionValue("d").toUpperCase());
            }

            if (cmd.hasOption("l"))
            {
                settings.put("logPath", cmd.getOptionValue("l", "." + File.separator + "jaxt-logs"));
            }

            if (cmd.hasOption("c"))
            {
                settings.put("commandsFile", cmd.getOptionValue("c", "." + File.separator + "commands.json"));
            }

            if (cmd.hasOption("p"))
            {
                settings.put("port", Integer.valueOf(cmd.getOptionValue("p")).intValue());
            }
            if (JavaKISSMain.settings.has("logPath"))
            {
                JavaKISSMain.logsFolder = new File(JavaKISSMain.settings.optString("logPath", "." + File.separator + "jaxt-logs"));
                if (!JavaKISSMain.logsFolder.exists())
                {
                    JavaKISSMain.logsFolder.mkdirs();
                }
            }
            if (!settings.has("hostname"))
            {
                settings.put("hostname", getLocalHostname());
            }
            KISSClient kClient = new KISSClient(settings.optString("host"), settings.optInt("port",8100));
            kClient.setTxDisabled(settings.optBoolean("txDisabled", false));
            JavaKISSMain jkm = new JavaKISSMain(kClient);
            JavaKISSMain.logAppend("main.log", "[STARTED]");
            JavaKISSMain.soundSystem = new SoundSystem();
            saveSettings();
            if (settings.has("terminal"))
            {
                JSONObject terminalSetup = settings.optJSONObject("terminal", new JSONObject());
                Set<String> keys = terminalSetup.keySet();
                for(String key : keys)
                {
                    final JSONObject tSetup = terminalSetup.optJSONObject(key, new JSONObject());
                    TerminalLink tl = new TerminalLink(kClient, key);
                    if (tSetup.optString("type","").equals("process"))
                    {
                        JSONArray command = tSetup.optJSONArray("execute");
                        final ArrayList<String> commandArray = new ArrayList<String>();
                        for(int i = 0; i < command.length(); i++)
                        {
                            commandArray.add(command.getString(i));
                        }
                        JavaKISSMain.mainLog("[TERMINAL LINK " + key + "] " + commandArray.stream().collect(Collectors.joining(" ")));
                        tl.addTerminalLinkListener(new TerminalLinkListener() {
                            @Override
                            public void onTerminalLinkSession(final TerminalLinkSession session) 
                            {
                                ProcessBuilder prb = new ProcessBuilder(commandArray.toArray(new String[commandArray.size()]));                        
                                session.setHandler(new ProcessTerminalLinkSessionHandler(prb));
                                JavaKISSMain.mainLog("[TERMINAL STARTED " + session.getTerminalCallsign() + "] " + session.getRemoteCallsign() + " " + commandArray.stream().collect(Collectors.joining(" ")));
                            }
                        });
                    }
                    
                }
            }
            

            Runtime.getRuntime().addShutdownHook(new Thread() 
            { 
                public void run() 
                {
                    JavaKISSMain.soundSystem.shutdown();
                    saveSettings();
                    System.err.println("Shutdown Complete");
                }
            });
            kClient.connect();
        } catch (Exception e) {
            //e.printStackTrace(System.err);
            System.err.println(e.getLocalizedMessage());
        }
    }

    public static void showHelp(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "jaxt", "Java AX25 Tool: A Java KISS TNC Client implementation", options, "Project Homepage - https://openstatic.org/projects/jaxt/" );
        System.exit(0);
    }

    @Override
    public void onReceived(AX25Packet packet)
    {
        JSONObject logEntry = packet.toJSONObject();
        jsonLogAppend("rx.json", logEntry);
        if (settings.optBoolean("verbose", false))
        {
            System.err.println("[" + this.simpleDateFormat.format(packet.getTimestamp()) + "] (Rx) " + packet.toLogString());
        }
        if (settings.has("postUrl"))
        {
            JavaKISSMain.postAX25Packet(settings.optString("postUrl"), packet);
        }
    }

    @Override
    public void onTransmit(AX25Packet packet)
    {
        JSONObject logEntry = packet.toJSONObject();
        jsonLogAppend("tx.json", logEntry);
        if (settings.optBoolean("verbose", false))
        {
            System.err.println("[" + this.simpleDateFormat.format(packet.getTimestamp()) + "] (Tx) " + packet.toLogString());
        }
    }

    public static synchronized void logAppend(String filename, String text)
    {
        String pattern = "HH:mm:ss yyyy-MM-dd";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        String logText = "[" + simpleDateFormat.format(new Date(System.currentTimeMillis())) + "] " + text;
        if (JavaKISSMain.logsFolder != null)
        {        
            try
            {
                
                File logFile = new File(JavaKISSMain.logsFolder, filename);
                File logFileParent = logFile.getParentFile();
                if (!logFileParent.exists())
                    logFileParent.mkdirs();
                FileOutputStream logOutputStream = new FileOutputStream(logFile, true);;
                PrintWriter logWriter = new PrintWriter(logOutputStream, true, Charset.forName("UTF-8"));
                logWriter.println(logText);
                logWriter.flush();
                logWriter.close();
                logOutputStream.close();
            } catch (Exception e) {

            }
        }
        if (JavaKISSMain.settings.optBoolean("verbose", false))
                System.err.println(logText);
    }

    public static synchronized void jsonLogAppend(String filename, JSONObject object)
    {
        if (JavaKISSMain.logsFolder != null)
        {
            Date now = new Date(object.optLong("timestamp", (new Date(System.currentTimeMillis())).getTime()));
            String pattern = "yyyy-MM-dd HH:mm:ss";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            try
            {
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
    }

    public static void mainLog(String text)
    {
        JavaKISSMain.logAppend("main.log", text);
        if (JavaKISSMain.apiWebServer != null)
        {
            JavaKISSMain.apiWebServer.broadcastINFO(text);
        }
    }

    public static void log(Exception e)
    {
        if (JavaKISSMain.logsFolder != null)
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
    }

    public static void saveSettings()
    {
        if (JavaKISSMain.settings.has("txTest"))
            JavaKISSMain.settings.remove("txTest");
        if (JavaKISSMain.settingsFile != null)
        {
            JavaKISSMain.settings.put("audio", JavaKISSMain.soundSystem.getAudioSettings());
            saveJSONObject(JavaKISSMain.settingsFile, JavaKISSMain.settings);
        }
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

    public static void postAX25Packet(String url, AX25Packet packet)
    {
        try
        {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            con.getOutputStream().write(packet.toJSONObject().toString().getBytes());
            InputStream inputStream = con.getInputStream();
            int responseCode = con.getResponseCode();
            InputStreamReader isr = new InputStreamReader(inputStream);
            Optional<String> optResponse = new BufferedReader(isr).lines().reduce((a, b) -> a + b);
            String output = "NO OUTPUT";
            if (optResponse.isPresent())
                output = optResponse.get();
            JavaKISSMain.logAppend("main.log", "[POST " + String.valueOf(responseCode) + "] " + url + " - " + output);
        } catch (Exception e) {
            JavaKISSMain.logAppend("main.log", "[POST ERROR] " + url);
            log(e);
        }
    }

    private static String[] JSONArrayToStringArray(JSONArray arry)
    {
        String[] args = new String[arry.length()];
        for (int i = 0; i < arry.length(); i++)
        {
            args[i] = arry.getString(i);
        }
        return args;
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

    public static String shellExec(String cmd[])
    {
        try
        {
            Process cmdProc = Runtime.getRuntime().exec(cmd);
            cmdProc.waitFor();
            return readStreamToString(cmdProc.getInputStream());
        } catch (Exception e) {
           
        }
        return null;
    }

    public static String readStreamToString(InputStream is)
    {
        String result = "";
        try
        {
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            result = s.hasNext() ? s.next() : "";
            s.close();
        } catch (Exception e) {}
        return result;
    }

    public static String getLocalHostname()
    {
        String returnValue = "";
        Map<String, String> env = System.getenv();
        if (env.containsKey("COMPUTERNAME"))
        {
            returnValue = env.get("COMPUTERNAME");
        } else if (env.containsKey("HOSTNAME")) {
            returnValue = env.get("HOSTNAME");
        } else {
            String hostnameCommand = shellExec(new String[] {"hostname"});
            if (hostnameCommand != null)
            {
                String hostname = hostnameCommand.trim();
                if (!"".equals(hostname))
                    returnValue = hostname;
            }
        }
        if ("".equals(returnValue))
        {
            try
            {
                for(Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces(); n.hasMoreElements() && "".equals(returnValue);)
                {
                    NetworkInterface ni = n.nextElement();
                    for(Enumeration<InetAddress> e = ni.getInetAddresses(); e.hasMoreElements() && "".equals(returnValue);)
                    {
                        InetAddress ia = e.nextElement();
                        if (!ia.isLoopbackAddress() && ia.isSiteLocalAddress())
                        {
                            String hostname = ia.getHostName();
                            returnValue = hostname;
                        }
                    }
                }

            } catch (Exception e) {}
        }
        if (returnValue.contains(".local"))
        {
            returnValue = returnValue.replace(".local", "");
        }
        if (returnValue.contains(".lan"))
        {
            returnValue = returnValue.replace(".lan", "");
        }
        return returnValue;
    }
}
