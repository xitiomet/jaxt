package org.openstatic.kiss;

import java.net.InetSocketAddress;

public interface AX25PacketListener 
{
    public void onKISSConnect(InetSocketAddress socketAddress);
    public void onKISSDisconnect(InetSocketAddress socketAddress);
    public void onReceived(AX25Packet packet);
}
