package org.openstatic.aprs.parser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;
import org.json.JSONObject;

public class Callsign implements Serializable
{
	private static final long serialVersionUID = 1L;
	protected String callsign;
	protected String ssid;
	private JSONObject hamDbRecord;


	public Callsign(String call) 
	{
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

	private void loadHamDBRecord()
    {
        try
        {
            String url = "https://api.hamdb.org/v1/" + URLEncoder.encode(this.callsign, "UTF-8") + "/json/jaxt";
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("GET");
            con.setDoOutput(false);
			con.setReadTimeout(10000);
			con.setConnectTimeout(10000);
            InputStream inputStream = con.getInputStream();
            int responseCode = con.getResponseCode();
            InputStreamReader isr = new InputStreamReader(inputStream);
            Optional<String> optResponse = new BufferedReader(isr).lines().reduce((a, b) -> a + b);
            String output = "NO OUTPUT";
            if (optResponse.isPresent())
            {
                output = optResponse.get();
                JSONObject response = new JSONObject(output);
                if (response.has("hamdb"))
                {
                    JSONObject hamdbObject = response.getJSONObject("hamdb");
                    if (hamdbObject.has("callsign"))
                    {
                        this.hamDbRecord = hamdbObject.getJSONObject("callsign");
                    }
                }
            } else {
				this.hamDbRecord = new JSONObject();
				this.hamDbRecord.put("error", "No Response");
			}
        } catch (Exception e) {
            this.hamDbRecord = new JSONObject();
			this.hamDbRecord.put("error", e.getMessage());
        }
    }

    public JSONObject getHamDBRecord()
    {
		if (this.hamDbRecord == null)
		{
			this.loadHamDBRecord();
		}
        return this.hamDbRecord;
    }

    public String getFullName()
    {
        return (this.getHamDBRecord().optString("fname", "") + " " + this.getHamDBRecord().optString("name", "")).trim();
    }

    public String getAddress()
    {
        return this.getHamDBRecord().optString("addr1", "") + "\n" +
               this.getHamDBRecord().optString("addr2", "") + " " + this.getHamDBRecord().optString("state", "") + " " + this.getHamDBRecord().optString("zip", "") + "\n" +
               this.getHamDBRecord().optString("country", "");
    }

    public String getLicenseClass()
    {
        String cl = this.getHamDBRecord().optString("class", "");
        if (cl.equals("T"))
            return "Technician";
        else if (cl.equals("E"))
            return "Extra";
        else if (cl.equals("G"))
            return "General";
        else
            return cl;
    }
}
