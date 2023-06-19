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
    }

    @Override
    public void onKISSConnect(InetSocketAddress socketAddress) {

    }

    @Override
    public void onKISSDisconnect(InetSocketAddress socketAddress) {

    }

    @Override
    public void onReceived(AX25Packet packet) {
        try
        {
            JSONObject jp = packet.toJSONObject();
            if (jp.optInt("control", 0) == 63)
            {
                AX25Packet pr = AX25Packet.buildResponse(packet, 99);
                this.kClient.send(pr);
                this.txCount = 0;
                this.rxCount = 0;
            }
            JSONArray cd = jp.getJSONArray("controlDecoded");
            if (JSONArrayContains(cd,"I"))
            {
                int seq = (packet.getControl() >> 1) & 0b111;
                System.err.println("seq: " + String.valueOf(seq));
                int newCtl = (((seq+1) << 5)  & 0b11100000) | 0b10001;
                AX25Packet pr = AX25Packet.buildResponse(packet, newCtl);
                this.kClient.send(pr);
            }
            /*
            if (JSONArrayContains(cd,"RR") &&  JSONArrayContains(cd,"C"))
            {
                int seq = (packet.getControl() >> 5) & 0b111;
                System.err.println("seq: " + String.valueOf(seq));
                int newCtl = (((seq+1) << 5)  & 0b11100000) | 0b10001;
                AX25Packet pr = AX25Packet.buildResponse(packet, newCtl);
                this.kClient.send(pr);
            }*/
        } catch (Exception tex) {
            tex.printStackTrace(System.err);
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