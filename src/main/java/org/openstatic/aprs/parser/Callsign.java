package org.openstatic.aprs.parser;

import java.io.Serializable;

public class Callsign implements Serializable
{
	private static final long serialVersionUID = 1L;
	protected String callsign;
	protected String ssid;

	public Callsign(String call) {
		String[] callssid = call.split("-");
		this.callsign = callssid[0].toUpperCase();
		if (callssid.length > 1) {
			this.ssid = callssid[1];
		} else {
			this.ssid="";
		}
	}

	public Callsign(byte[] data, int offset)
	{
		byte[] shifted = new byte[6];
		byte ssidbyte = data[offset + 6];
		for (int i = 0; i < 6; i++)
			shifted[i] = (byte)((data[offset + i]&0xff) >> 1);
		this.callsign = new String(shifted, 0, 6).trim();
		int ssidval = (ssidbyte & 0x1e) >> 1;
		if (ssidval != 0)
			this.ssid = "" + ssidval;
		else this.ssid = "";
	}

    public String getCallsign()
	{
        return callsign;
    }

    public void setCallsign(String callsign)
	{
        this.callsign = callsign.toUpperCase();
    }

    public String getSsid()
	{
        return ssid;
    }

    public void setSsid(String ssid)
	{
        this.ssid = ssid;
    }

    @Override
    public String toString() 
	{
        return callsign + (ssid == "" ? "" : "-" + ssid);
    }

    public byte[] toAX25() throws IllegalArgumentException
	{
        byte[] callbytes = callsign.getBytes();
        byte[] ax25 = new byte[7];
		java.util.Arrays.fill(ax25, (byte)0x40);
		if (callbytes.length > 6)
			throw new IllegalArgumentException("Callsign " + callsign + " is too long for AX.25!");
		for (int i = 0; i < callbytes.length; i++) {
			ax25[i] = (byte)(callbytes[i] << 1);
		}
		int ssidval = 0;
		try {
			ssidval = Integer.parseInt(ssid);
		} catch (NumberFormatException e) {
		}
		ax25[6] = (byte) (0x60 | ((ssidval*2) & 0x1e));
		return ax25;
    }
}
