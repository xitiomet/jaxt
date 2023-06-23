package org.openstatic.kiss;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONObject;

public class TerminalLinkSession implements Runnable
{
    private int txModulus;
    private int rxModulus;
    private String callsign;
    private TerminalLink link;
    private TerminalLinkSessionHandler handler;
    private LinkedBlockingQueue<String> outboundQueue;
    private boolean remoteReceiveReady;
    private long lastRxAt; // last time we received a packet
    private long lastTxAt; // last time we sent a packet
    private long lastSABMAt;
    private boolean sabmComplete;
    private Thread monitorThread;
    private boolean connected;
    private AX25Packet[] pendingPacket;

    public TerminalLinkSession(TerminalLink link, String callsign)
    {
        this.link = link;
        this.callsign = callsign;
        this.txModulus = 0;
        this.rxModulus = 0;
        this.sabmComplete = false;
        this.lastSABMAt = System.currentTimeMillis();
        this.remoteReceiveReady = false;
        this.connected = true;
        this.outboundQueue = new LinkedBlockingQueue<String>();
        this.pendingPacket = new AX25Packet[8];
        this.lastRxAt = System.currentTimeMillis();
        this.lastTxAt = 0;
        this.monitorThread = new Thread(this);
        this.monitorThread.start();
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
        cArray.put("R" + String.valueOf(this.rxModulus));
        cArray.put("S" + String.valueOf(this.txModulus));
        cArray.put("C");
        jPacket.put("control", cArray);
        AX25Packet packet = new AX25Packet(jPacket);
        try
        {
            this.remoteReceiveReady = false;
            this.lastTxAt = System.currentTimeMillis();
            this.pendingPacket[packet.getSendModulus()] = packet;
            this.link.getKISSClient().send(packet);
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
        if (packet.controlContains("SABM"))
        {
            this.lastSABMAt = System.currentTimeMillis();
            JSONObject jPacket = new JSONObject();
            jPacket.put("source", this.link.getCallsign());
            jPacket.put("destination", this.callsign);
            JSONArray respCtrl = new JSONArray();
            respCtrl.put("UA");
            respCtrl.put("R");
            jPacket.put("control",respCtrl);
            AX25Packet pr = new AX25Packet(jPacket);
            try
            {
                TerminalLinkSession.this.link.getKISSClient().send(pr);
            } catch (Exception tex) {}
        }

        // Numbered information frame
        if (packet.controlContains("I"))
        {
            if (packet.getSendModulus() == this.rxModulus)
            {
                if (this.handler != null)
                {
                    this.handler.onData(this, packet.getPayload());
                }
                this.rxModulus++;
                if (this.rxModulus >= 8) this.rxModulus = 0;
            } else {
                //System.err.println("Skipping received packet");
            }
            // after handling the packet, respond with a RR
            JSONArray respCtrl = new JSONArray();
            respCtrl.put("RR");
            respCtrl.put("R" + String.valueOf(this.rxModulus));
            respCtrl.put("R");
            AX25Packet pr = AX25Packet.buildResponse(packet,respCtrl);
            try
            {
                this.link.getKISSClient().send(pr);
            } catch (Exception e) {}
        }

        // Received Ready
        if (packet.controlContains("RR"))
        {
            // respond to RR requests from client
            if (packet.controlContains("C"))
            {
                JSONArray respCtrl = new JSONArray();
                respCtrl.put("RR");
                respCtrl.put("R" + String.valueOf(this.rxModulus));
                AX25Packet pr = AX25Packet.buildResponse(packet, respCtrl);
                try
                {
                    this.link.getKISSClient().send(pr);
                } catch (Exception e) {}
            } else if (packet.controlContains("R") && !this.remoteReceiveReady) {
                // reset our flag and send more packets when client responds with a RR
                this.txModulus = packet.getReceiveModulus();
                this.pendingPacket[this.txModulus] = null;
                this.remoteReceiveReady = true;
                sendFromQueue();
            }
        }

        if (packet.controlContains("REJ") && packet.controlContains("R"))
        {
            int nextTxMod = ((this.txModulus + 1) % 8);
            // They are rejecting because we need to move on to the next packet treat this like an RR
            if (nextTxMod == packet.getReceiveModulus())
            {
                this.txModulus = packet.getReceiveModulus();
                this.pendingPacket[this.txModulus] = null;
                this.remoteReceiveReady = true;
                sendFromQueue();
            }
        }
    }

    public long lastTxAge()
    {
        return System.currentTimeMillis() - this.lastTxAt;
    }

    public long lastRxAge()
    {
        return System.currentTimeMillis() - this.lastRxAt;
    }

    @Override
    public void run()
    {
        while(this.connected && this.link != null)
        {
            long now = System.currentTimeMillis();
            try
            {
                if (!this.sabmComplete)
                { // SABM has not completed
                    if (((now - this.lastSABMAt) < 7000) && this.lastTxAge() > 2000)
                    {   //Remote is still waiting for UA
                        JSONObject jPacket = new JSONObject();
                        jPacket.put("source", this.link.getCallsign());
                        jPacket.put("destination", this.callsign);
                        JSONArray respCtrl = new JSONArray();
                        respCtrl.put("UA");
                        respCtrl.put("R");
                        jPacket.put("control",respCtrl);
                        AX25Packet pr = new AX25Packet(jPacket);
                        TerminalLinkSession.this.link.getKISSClient().send(pr);
                    } else {
                        // switch over to fully ready mode SABM complete
                        this.sabmComplete = true;
                        this.remoteReceiveReady = true;
                        this.sendFromQueue();
                    }
                } else {
                    // We are waiting for the remote side to give us a RR
                    if (!this.remoteReceiveReady)
                    {
                        // We sent something, maybe they didnt get it?
                        if (this.pendingPacket[this.txModulus] != null)
                        {
                            // ok it was sent 10 seconds ago, lets try again
                            if (this.lastTxAge() > 10000)
                            {
                                this.link.getKISSClient().send(this.pendingPacket[this.txModulus]);
                                this.lastTxAt = now;
                            }
                        } else {

                        }
                    }

                    // If this connection goes quiet for 5 minutes, there is a good chance its dead, lets kill it
                    if (this.lastRxAge() > 300000)
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
                }
                
            } catch (Exception rxx) {}
            try
            {
                // sleep for a second
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }
}
