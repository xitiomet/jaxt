package org.openstatic.kiss;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class APITermProcessHandler implements Runnable
{
    private ProcessBuilder processBuilder;
    private Process process;
    private PrintWriter pw;
    private BufferedReader std_br;
    private Thread thread;
    private long termId;
    private boolean wasKilled;

    public APITermProcessHandler(long termId, ProcessBuilder builder)
    {
        this.wasKilled = false;
        this.processBuilder = builder;
        this.termId = termId;
        try
        {
            this.process = this.processBuilder.redirectErrorStream(true).start();
            InputStream is = this.process.getInputStream();
            OutputStream os = this.process.getOutputStream();
            this.std_br = new BufferedReader(new InputStreamReader(is));
            this.pw = new PrintWriter(os);
            this.thread = new Thread(this);
            this.thread.start();
        } catch (Exception e) {
            APIWebServer.instance.writeTerm(termId, e.getLocalizedMessage() + "\r\n");
            APIWebServer.instance.promptTerm(this.termId);
        }
    }

    public void println(String data) 
    {
        pw.println(data.trim());
        pw.flush();
    }

    public void kill() 
    {
        try
        {
            this.wasKilled = true;
            this.process.destroy();
        } catch (Exception e) {
            
        }
    }

    @Override
    public void run() 
    {
        try
        {
            while(this.process.isAlive() || std_br.ready())
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
                        APIWebServer.instance.writeTerm(this.termId, line);
                    }
                    Thread.sleep(100);
                } catch (Exception dr) {
                    dr.printStackTrace(System.err);
                }
            }
        } catch (Exception lpe) {
            lpe.printStackTrace(System.err);
        }
        if (!wasKilled)
            APIWebServer.instance.promptTerm(this.termId);
    }
}
