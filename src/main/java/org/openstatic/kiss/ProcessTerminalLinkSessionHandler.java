package org.openstatic.kiss;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class ProcessTerminalLinkSessionHandler implements TerminalLinkSessionHandler, Runnable
{
    private ProcessBuilder processBuilder;
    private Process process;
    private PrintWriter pw;
    private BufferedReader std_br;
    private TerminalLinkSession session;
    private Thread thread;
    private boolean wasKilled;

    public ProcessTerminalLinkSessionHandler(ProcessBuilder builder)
    {
        this.processBuilder = builder.redirectErrorStream(true);
        this.wasKilled = false;
    }

    @Override
    public void onData(TerminalLinkSession session, String data) 
    {
        pw.println(data.trim());
        pw.flush();
    }

    @Override
    public void onDisconnect(TerminalLinkSession session) 
    {        
        this.process.destroy();
        this.wasKilled = true;
    }

    @Override
    public void run() 
    {
        while(this.process.isAlive())
        {
            try
            {
                StringBuffer sb = new StringBuffer();
                while (std_br.ready())
                {
                    sb.append((char) std_br.read());
                }
                if (sb.length() > 0)
                {
                    String line = sb.toString().replaceAll("(?<!\r)\n", "\r\n");
                    session.sendText(line);
                }
                Thread.sleep(100);
            } catch (Exception dr) {
                dr.printStackTrace(System.err);
            }
        }
        if (!wasKilled)
        {
            this.session.disconnect();
        }
    }

    @Override
    public void onConnect(TerminalLinkSession session) 
    {
        this.session = session;
        try
        {
            this.process = this.processBuilder.start();
            InputStream is = this.process.getInputStream();
            OutputStream os = this.process.getOutputStream();
            this.std_br = new BufferedReader(new InputStreamReader(is));
            this.pw = new PrintWriter(os);
            this.thread = new Thread(this);
            this.thread.start();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
