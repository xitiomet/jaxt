package org.openstatic.kiss;

import java.net.InetSocketAddress;

import org.json.JSONArray;
import org.json.JSONObject;

public class TerminalLink implements AX25PacketListener
{
    private KISSClient kClient;
    private int txCount;
    private int rxCount;
    private String callsign;

    public TerminalLink(KISSClient kClient, String callsign)
    {
        this.kClient = kClient;
        this.callsign = callsign;
        this.txCount = 0;
        this.rxCount = 0;
        this.kClient.addAX25PacketListener(this);
    }

    @Override
    public void onKISSConnect(InetSocketAddress socketAddress) {

    }

    @Override
    public void onKISSDisconnect(InetSocketAddress socketAddress) {

    }

    private void processText(String source, String txt)
    {
        String rtxt = "I dont know what \"" + txt.trim() + "\" means!!\n";      
        try
        {  
            this.kClient.send(buildIFrame(source, rtxt));
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private AX25Packet buildIFrame(String target, String text)
    {
        JSONObject jPacket = new JSONObject();
        jPacket.put("source", this.callsign);
        jPacket.put("destination", target);
        jPacket.put("payload", text);
        JSONArray cArray = new JSONArray();
        cArray.put("I");
        cArray.put("R" + String.valueOf(this.rxCount));
        cArray.put("S" + String.valueOf(this.txCount));
        cArray.put("C");
        jPacket.put("control", cArray);
        AX25Packet packet = new AX25Packet(jPacket);
        txCount++;
        if (txCount >= 8) txCount = 0;
        return packet;
    }

    @Override
    public void onReceived(AX25Packet packet)
    {
        if (packet.getDestinationCallsign().equals(this.callsign))
        {
            try
            {
                if (packet.controlContains("SABM"))
                {
                    JSONArray respCtrl = new JSONArray();
                    respCtrl.put("UA");
                    AX25Packet pr = AX25Packet.buildResponse(packet, respCtrl);
                    this.kClient.send(pr);
                    this.txCount = 0;
                    this.rxCount = 0;
                }
                if (packet.controlContains("I"))
                {
                    this.rxCount = packet.getSendModulus()+1;
                    // increment by one and respond letting remote host know you received it
                    JSONArray respCtrl = new JSONArray();
                    respCtrl.put("RR");
                    respCtrl.put("R" + String.valueOf(this.rxCount));
                    AX25Packet pr = AX25Packet.buildResponse(packet, respCtrl);
                    this.kClient.send(pr);
                    processText(packet.getSourceCallsign(), packet.getPayload());
                }
                if (packet.controlContains("RR") && packet.controlContains("C"))
                {
                    JSONArray respCtrl = new JSONArray();
                    respCtrl.put("RR");
                    respCtrl.put("R" + String.valueOf(this.rxCount));
                    AX25Packet pr = AX25Packet.buildResponse(packet, respCtrl);
                    this.kClient.send(pr);
                }
            } catch (Exception tex) {
                tex.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void onTransmit(AX25Packet packet)
    {
        
    }


    public static boolean JSONArrayContains(JSONArray a, String needle)
    {
        for (int i = 0; i < a.length(); i++)
        {
            if (needle.equals(a.optString(i, null)))
            {
                return true;
            }
        }
        return false;
    }

}