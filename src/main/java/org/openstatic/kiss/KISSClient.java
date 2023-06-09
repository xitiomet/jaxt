package org.openstatic.kiss;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

import javax.net.SocketFactory;

public class KISSClient implements Runnable
{
    private Socket socket;
    private InetSocketAddress address;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread connectionThread;
    private boolean keepRunning;
    private boolean connected;
    private KissProcessor kissProcessor;
    private ArrayList<AX25PacketListener> listeners;

    public KISSClient(String ipAddress, int port) throws IOException
    {
        this.keepRunning = true;
        this.connected = false;
        this.kissProcessor = new KissProcessor(this, (byte) 8);
        this.address = new InetSocketAddress(ipAddress, port);
        this.connect();
        this.listeners = new ArrayList<AX25PacketListener>();
        this.connectionThread = new Thread(this);
        this.connectionThread.start();
    }

    public void addAX25PacketListener(AX25PacketListener listener)
    {
        if (!this.listeners.contains(listener))
            this.listeners.add(listener);
    }

    public void removeAX25PacketListener(AX25PacketListener listener)
    {
        if (this.listeners.contains(listener))
            this.listeners.remove(listener);
    }

    public void connect()
    {
        try
        {
            if (this.socket != null)
            {
                if (!this.socket.isClosed())
                    this.socket.close();
            }
        } catch (Exception e1) {}
        try
        {
            this.socket = SocketFactory.getDefault().createSocket();
            this.socket.connect(this.address);
            this.inputStream = this.socket.getInputStream();
            this.outputStream = this.socket.getOutputStream();
            this.connected = true;
        } catch (Exception e) {}
    }

    @Override
    public void run() 
    {
        while(this.keepRunning)
        {
            try
            {
                if (!this.connected)
                {
                    //System.err.println("Reconnect");
                    this.connect();
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
            try
            {
                int avail = this.inputStream.available();
                if (avail > 0)
                {
                    byte[] bb = new byte[1024];
                    this.inputStream.read(bb);
                    this.kissProcessor.receive(bb);
                }
            } catch (Exception e) {
               this.connected = false;
            }
            try
            {
                Thread.sleep(100);
            } catch (Exception sleepError) {}
        }
    }

    protected void onKPSend(byte[] data) 
    {
        try
        {
            this.outputStream.write(data);
        } catch (Exception e) {
            this.connected = false;
        }
    }

    protected void onKPReceive(byte[] frame) 
    {
        Packet packet = new Packet(frame);
        this.listeners.forEach((l) -> l.onReceived(packet));        
    }

    private void send(byte[] data) throws IOException
    {
        this.kissProcessor.startKissPacket(kissProcessor.KISS_CMD_DATA);
        for (byte b : data) {
            this.kissProcessor.sendKissByte(b);
        }
        this.kissProcessor.completeKissPacket();
    }

    public void send(Packet packet)
    {
        try
        {
            this.send(packet.bytesWithoutCRC());
        } catch (Exception e) {}
    }

    public void sent(String from, String to, String data)
    {
        Packet packet = new Packet(from, to, data);
        this.send(packet);
    }
}
