package org.openstatic.kiss;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class TerminalLink implements AX25PacketListener
{
    private KISSClient kClient;
    private String callsign;
    private HashMap<String, TerminalLinkSession> sessions;
    private ArrayList<TerminalLinkListener> listeners;

    public TerminalLink(KISSClient kClient, String callsign)
    {
        this.sessions = new HashMap<String, TerminalLinkSession>();
        this.listeners = new ArrayList<TerminalLinkListener>();
        this.kClient = kClient;
        this.callsign = callsign;
        this.kClient.addAX25PacketListener(this);
    }
    
    public void addTerminalLinkListener(TerminalLinkListener listener)
    {
        if (!this.listeners.contains(listener))
            this.listeners.add(listener);
    }

    public KISSClient getKISSClient()
    {
        return this.kClient;
    }

    public String getCallsign()
    {
        return this.callsign;
    }

    @Override
    public void onKISSConnect(InetSocketAddress socketAddress) {

    }

    @Override
    public void onKISSDisconnect(InetSocketAddress socketAddress) {

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
                    TerminalLinkSession session = new TerminalLinkSession(this, packet.getSourceCallsign());
                    this.sessions.put(packet.getSourceCallsign(), session);
                    this.listeners.forEach((l) -> l.onTerminalLinkSession(session));
                }
                if (packet.controlContains("DISC"))
                {
                    TerminalLinkSession session = this.sessions.get(packet.getSourceCallsign());
                    if (session != null)
                    {
                        JSONArray respCtrl = new JSONArray();
                        respCtrl.put("UA");
                        AX25Packet pr = AX25Packet.buildResponse(packet, respCtrl);
                        this.kClient.send(pr);
                        this.sessions.remove(packet.getSourceCallsign());
                        session.handleDisconnect();
                    }
                }
                if (packet.controlContains("I") || packet.controlContains("RR") || packet.controlContains("UA"))
                {
                    TerminalLinkSession session = this.sessions.get(packet.getSourceCallsign());
                    if (session != null)
                    {
                        session.handleFrame(packet);                        
                    }
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