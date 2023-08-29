package org.openstatic.sound;

import java.io.IOException;
import java.io.OutputStream;

import javax.sound.sampled.SourceDataLine;

public class SourceDataLineOutputStream extends OutputStream
{
    private SourceDataLine sourceDataLine;

    public SourceDataLineOutputStream(SourceDataLine sourceDataLine)
    {
        super();
        this.sourceDataLine = sourceDataLine;
    }

    public SourceDataLine getSourceDataLine()
    {
        return this.sourceDataLine;
    }

    public boolean isAlive()
    {
        if (this.sourceDataLine != null)
        {
            if (this.sourceDataLine.isRunning())
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        if (this.sourceDataLine != null)
        {
            if (!this.sourceDataLine.isOpen())
            {
                throw new IOException("Cannot write, sourceDataLine is dead!");
            }
            int written = this.sourceDataLine.write(b, off, len);
            //System.err.println("Wrote " + String.valueOf(written));
        } else {
            throw new IOException("Cannot write, no sourceDataLine!");
        }
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        if (this.sourceDataLine != null)
        {
            if (!this.sourceDataLine.isOpen())
            {
                throw new IOException("Cannot write, sourceDataLine is dead!");
            }
            int written = this.sourceDataLine.write(b, 0, b.length);
            //System.err.println("Wrote " + String.valueOf(written));
        } else {
            throw new IOException("Cannot write, no sourceDataLine!");
        }
    }

    @Override
    public void write(int b) throws IOException 
    {
        throw new IOException("Cannot write single bytes to this stream!");
    }

    @Override
    public void flush()
    {
        if (this.sourceDataLine != null)
            this.sourceDataLine.flush();
    }
}
