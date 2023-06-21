package org.openstatic.kiss;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class ProcessTerminalLinkSessionHandler implements TerminalLinkSessionHandler, Runnable
{
    private Process process;
    private PrintWriter pw;
    private BufferedReader std_br;
    private BufferedReader err_br;
    private TerminalLinkSession session;
    private Thread thread;

    public ProcessTerminalLinkSessionHandler(ProcessBuilder builder)
    {
        try
        {
            this.process = builder.start();
            InputStream is = this.process.getInputStream();
            InputStream es = this.process.getErrorStream();
            OutputStream os = this.process.getOutputStream();
            this.std_br = new BufferedReader(new InputStreamReader(is));
            this.err_br = new BufferedReader(new InputStreamReader(es));
            this.pw = new PrintWriter(os);
            this.thread = new Thread(this);
            this.thread.start();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Override
    public void onData(TerminalLinkSession session, String data) 
    {
        //System.err.println("DATA ARRIVED: " + data);
        pw.println(data.trim());
        pw.flush();
    }

    @Override
    public void onDisconnect(TerminalLinkSession session) 
    {        
        this.process.destroy();
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
                while (err_br.ready())
                {
                    sb.append((char) err_br.read());
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
    }

    @Override
    public void onConnect(TerminalLinkSession session) {
        this.session = session;
    }
}
