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
    private boolean allowKissPing;
    private KissProcessor kissProcessor;
    private ArrayList<AX25PacketListener> listeners;
    private long lastKissPing;
    private boolean txDisabled;
    private int txNumber;

    public KISSClient(String ipAddress, int port) throws IOException
    {
        this.keepRunning = true;
        this.connected = false;
        this.kissProcessor = new KissProcessor(this, (byte) 8);
        this.address = new InetSocketAddress(ipAddress, port);
        this.listeners = new ArrayList<AX25PacketListener>();
        this.allowKissPing = false;
        this.txDisabled = false;
        this.txNumber = 1;
    }

    public void setTxDisabled(boolean txDisabled)
    {
        this.txDisabled = txDisabled;
    }

    public void setKissPing(boolean v)
    {
        this.allowKissPing = v;
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

    public void disconnect()
    {
        this.keepRunning = false;
        try
        {
            this.socket.close();
        } catch (Exception e) {

        }
        this.fireDisconnect();
    }

    public void connect()
    {
        //System.err.println("Connection attempt...");
        if (!this.connected)
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
                this.kissProcessor = new KissProcessor(this, (byte) 8);
                this.socket = SocketFactory.getDefault().createSocket();
                this.socket.connect(this.address);
                this.socket.setSoTimeout(4000);
                this.inputStream = this.socket.getInputStream();
                this.outputStream = this.socket.getOutputStream();
                this.connected = true;
                this.keepRunning = true;
                this.listeners.forEach((l) -> l.onKISSConnect(this.address));
            } catch (Exception e) {}
        }
        if (this.connectionThread != null)
        {
            if (!this.connectionThread.isAlive())
            {
                this.connectionThread = new Thread(this);
                this.connectionThread.start();
            }
        } else {
            this.connectionThread = new Thread(this);
            this.connectionThread.start();
        }
    }

    public void kissPing()
    {
        this.lastKissPing = System.currentTimeMillis();
        try
        {
            //System.err.println("KissPING");
            this.outputStream.write((byte)0xc0);
            this.outputStream.flush();
        } catch (Exception e) {
            //System.err.println("KissPING Error");
            fireDisconnect();
        }
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
                } else {
                    if (System.currentTimeMillis() - this.lastKissPing > 10000 && this.allowKissPing)
                    {
                        kissPing();
                    }
                }
            } catch (Exception e) {
                //e.printStackTrace(System.err);
            }
            if (this.inputStream != null)
            {
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
                    //e.printStackTrace(System.err);
                    fireDisconnect();
                }
            }
            try
            {
                Thread.sleep(100);
            } catch (Exception sleepError) {}
        }
        this.connectionThread = null;
    }

    protected void onKPSend(byte[] data) throws IOException
    {
        if (!this.txDisabled)
        {
            try
            {
                this.outputStream.write(data);
                this.lastKissPing = System.currentTimeMillis();
                this.txNumber++;
            } catch (IOException e) {
                fireDisconnect();
                throw e;
            }
        }
    }

    public boolean isConnected()
    {
        return this.connected;
    }

    private void fireDisconnect()
    {
        if (this.connected)
        {
            this.connected = false;
            this.listeners.forEach((l) -> l.onKISSDisconnect(this.address));
        }
    }

    protected void onKPReceive(byte[] frame) 
    {
        try
        {
            AX25Packet packet = new AX25Packet(frame);
            packet.setDirection("rx");
            this.listeners.forEach((l) -> l.onReceived(packet));
        } catch (Exception e) {
            
        }
        this.lastKissPing = System.currentTimeMillis();
    }

    private void send(byte[] data) throws IOException
    {
        if (!this.txDisabled)
        {
            this.kissProcessor.startKissPacket(kissProcessor.KISS_CMD_DATA);
            for (byte b : data) {
                this.kissProcessor.sendKissByte(b);
            }
            this.kissProcessor.completeKissPacket();
        }
    }

    public void send(AX25Packet packet) throws IOException
    {
        if (!this.txDisabled)
        {
            packet.setDirection("tx");
            packet.updatePayloadVar("{{ts}}", System.currentTimeMillis());
            packet.updatePayloadVar("{{seq}}", this.txNumber);
            this.send(packet.bytesWithoutCRC());
            this.listeners.forEach((l) -> l.onTransmit(packet));
        }
    }

    public void send(String from, String to, String data) throws IOException
    {
        AX25Packet packet = AX25Packet.buildPacket(from, to, data);
        this.send(packet);
    }
}
