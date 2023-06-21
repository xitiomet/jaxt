package org.openstatic.kiss;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONObject;

public class TerminalLinkSession implements Runnable
{
    private int txCount;
    private int rxCount;
    private String callsign;
    private TerminalLink link;
    private TerminalLinkSessionHandler handler;
    private LinkedBlockingQueue<String> outboundQueue;
    private boolean remoteReceiveReady;
    private long notReadyAt;
    private long lastRxAt;
    private Thread monitorThread;
    private boolean connected;

    public TerminalLinkSession(TerminalLink link, String callsign)
    {
        this.link = link;
        this.callsign = callsign;
        this.txCount = 0;
        this.rxCount = 0;
        this.remoteReceiveReady = true;
        this.connected = true;
        this.outboundQueue = new LinkedBlockingQueue<String>();
        this.monitorThread = new Thread(this);
        this.monitorThread.start();
        this.notReadyAt = 0;
        this.lastRxAt = System.currentTimeMillis();
    }

    public void setHandler(TerminalLinkSessionHandler handler)
    {
        this.handler = handler;
        this.handler.onConnect(this);
    }
    
    public String getRemoteCallsign()
    {
        return this.callsign;
    }

    public void sendText(String text)
    {
        this.outboundQueue.addAll(splitString(text));
        if (remoteReceiveReady)
        {
            sendFromQueue();
        }
    }

    private void sendFromQueue()
    {
        if (this.outboundQueue.size() > 0)
        {
            String dataOut = this.outboundQueue.poll();
            //System.err.println("send from queue: " + dataOut);
            sendIFrame(dataOut);
        }
    }

    private void sendIFrame(String text)
    {
        JSONObject jPacket = new JSONObject();
        jPacket.put("source", this.link.getCallsign());
        jPacket.put("destination", this.callsign);
        jPacket.put("payload", text);
        JSONArray cArray = new JSONArray();
        cArray.put("I");
        cArray.put("R" + String.valueOf(this.rxCount));
        cArray.put("S" + String.valueOf(this.txCount));
        cArray.put("C");
        jPacket.put("control", cArray);
        AX25Packet packet = new AX25Packet(jPacket);
        try
        {
            this.link.getKISSClient().send(packet);
            txCount++;
            if (txCount >= 8) txCount = 0;
            this.remoteReceiveReady = false;
            this.notReadyAt = System.currentTimeMillis();
        } catch (Exception e) {}
    }

    protected void handleDisconnect()
    {
        if (this.connected)
        {
            if (this.handler != null)
                this.handler.onDisconnect(this);
            this.link = null;
            this.connected = false;
        }
    }

    private static ArrayList<String> splitString(String str) 
    {
        ArrayList<String> result = new ArrayList<>();
        
        // Split the string into chunks of maximum size 255
        while(str.length() > 0) {
            int endIndex = Math.min(str.length(), 255);
            String chunk = str.substring(0, endIndex);
            result.add(chunk);
            str = str.substring(endIndex);
        }
        
        return result;
    }
    

    protected void handleFrame(AX25Packet packet)
    {
        this.lastRxAt = System.currentTimeMillis();
        if (packet.controlContains("I"))
        {
            if (this.handler != null)
                this.handler.onData(this, packet.getPayload());
            this.rxCount = packet.getSendModulus()+1;
            JSONArray respCtrl = new JSONArray();
            respCtrl.put("RR");
            respCtrl.put("R" + String.valueOf(this.rxCount));
            respCtrl.put("R");
            AX25Packet pr = AX25Packet.buildResponse(packet,respCtrl);
            try
            {
                this.link.getKISSClient().send(pr);
            } catch (Exception e) {}
        }
        if (packet.controlContains("RR"))
        {
            if (packet.controlContains("C"))
            {
                JSONArray respCtrl = new JSONArray();
                respCtrl.put("RR");
                respCtrl.put("R" + String.valueOf(this.rxCount));
                AX25Packet pr = AX25Packet.buildResponse(packet, respCtrl);
                try
                {
                    this.link.getKISSClient().send(pr);
                } catch (Exception e) {}
            } else if (packet.controlContains("R")) {
                this.remoteReceiveReady = true;
                sendFromQueue();
            }
        }
    }

    @Override
    public void run()
    {
        while(this.connected && this.link != null)
        {
            long now = System.currentTimeMillis();
            try
            {
                if (!this.remoteReceiveReady)
                {
                    if (now - this.notReadyAt > 3000)
                    {
                        JSONObject jPacket = new JSONObject();
                        jPacket.put("source", this.link.getCallsign());
                        jPacket.put("destination", this.callsign);
                        JSONArray cArray = new JSONArray();
                        cArray.put("RR");
                        cArray.put("R" + String.valueOf(this.rxCount));
                        cArray.put("C");
                        jPacket.put("control", cArray);
                        AX25Packet packet = new AX25Packet(jPacket);
                        this.link.getKISSClient().send(packet);
                        this.notReadyAt = System.currentTimeMillis();
                    }
                }
                if ((now - this.lastRxAt) > 500000)
                {
                    JSONObject jPacket = new JSONObject();
                    jPacket.put("source", this.link.getCallsign());
                    jPacket.put("destination", this.callsign);
                    JSONArray cArray = new JSONArray();
                    cArray.put("DISC");
                    cArray.put("C");
                    jPacket.put("control", cArray);
                    AX25Packet packet = new AX25Packet(jPacket);
                    this.link.getKISSClient().send(packet);
                    this.handleDisconnect();
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }
}
