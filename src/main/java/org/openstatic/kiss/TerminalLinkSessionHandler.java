package org.openstatic.kiss;

public interface TerminalLinkSessionHandler 
{
    public void onData(TerminalLinkSession session, String data);
    public void onDisconnect(TerminalLinkSession session);
    public void onConnect(TerminalLinkSession session);
}
