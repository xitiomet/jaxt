package org.openstatic.kiss;

import org.apache.commons.cli.*;

public class JavaKISSMain implements AX25PacketListener, Runnable
{
    private KISSClient kClient;
    private Thread mainThread;

    public JavaKISSMain(KISSClient client, boolean txTest)
    {
        this.kClient = client;
        client.addAX25PacketListener(this);
        this.mainThread = new Thread(this);
        if (txTest)
            this.mainThread.start();
    }

    public void run()
    {
        while(true)
        {
            try
            {
                Thread.sleep(10000);
                String payload = "Test Transmission " + String.valueOf(System.currentTimeMillis());
                Packet packet = new Packet("XXX", "XYZ", payload);
                kClient.send(packet);
            } catch (Exception e) {}
        }
    }

    public static void main(String[] args)
    {
        CommandLine cmd = null;
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        String host = "127.0.0.1";
        int port = 8100;
        boolean txTest = false;
        options.addOption(new Option("t", "test", false, "Send test packets"));
        options.addOption(new Option("h", "host", true, "Specify TNC host"));
        options.addOption(new Option("p", "port", true, "KISS Port"));
        options.addOption(new Option("?", "help", false, "Shows help"));
        try
        {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("?"))
            {
                showHelp(options);
            }

            if (cmd.hasOption("t"))
            {
                txTest = true;
            }

            if (cmd.hasOption("h"))
            {
                host = cmd.getOptionValue("h");
            }

            if (cmd.hasOption("p"))
            {
                port = Integer.valueOf(cmd.getOptionValue("p")).intValue();
            }

            KISSClient kClient = new KISSClient(host, port);
            JavaKISSMain jkm = new JavaKISSMain(kClient, txTest);
           
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
        System.err.println("FROM " + packet.getSourceCallsign() + " TO " + packet.getDestinationCallsign() + ":");
        System.err.println(packet.getPayloadAsString());
        System.err.println();
    }
}
