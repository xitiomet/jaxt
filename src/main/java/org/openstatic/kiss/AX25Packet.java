package org.openstatic.kiss;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collector;

import org.json.JSONArray;
import org.json.JSONObject;

public class AX25Packet 
{
	private String source, destination;
	private String[] path;
	private String payload;
	private Date timestamp;
	private JSONArray control;
	private int protocol;
	private String direction;
	private String commandResponse;
	//private JSONArray control;

    private final int AX25_CRC_CORRECT   = 0xF0B8;
	private final int CRC_CCITT_INIT_VAL = 0xFFFF;
	private final int MAX_FRAME_SIZE = // not including delimiting flags
		                   7+7            // source and destination
		                  +(8*7)          // path
		                  +1+1            // control and PID
		                  +256            // information
		                  +2;             // frame checksum
	
	public static final int AX25_CONTROL_UI                = 0b00000011;
	public static final int AX25_CONTROL_DISC              = 0b01010011;
	public static final int AX25_CONTROL_SABM              = 0b00111111;
	public static final int AX25_CONTROL_SABME             = 0b01111111;
	public static final int AX25_CONTROL_UA                = 0b01100011;

	public static final int AX25_CONTROL_DM                = 0b00001111;
	public static final int AX25_CONTROL_FRMR              = 0b10000111;

	public static final int AX25_PROTOCOL_COMPRESSED_TCPIP   = 0x06;
	public static final int AX25_PROTOCOL_UNCOMPRESSED_TCPIP = 0x07;
	public static final int AX25_PROTOCOL_NO_LAYER_3         = 0xF0; // used for APRS

	private static final int crc_ccitt_tab[] = {
	        0x0000, 0x1189, 0x2312, 0x329b, 0x4624, 0x57ad, 0x6536, 0x74bf,
	        0x8c48, 0x9dc1, 0xaf5a, 0xbed3, 0xca6c, 0xdbe5, 0xe97e, 0xf8f7,
	        0x1081, 0x0108, 0x3393, 0x221a, 0x56a5, 0x472c, 0x75b7, 0x643e,
	        0x9cc9, 0x8d40, 0xbfdb, 0xae52, 0xdaed, 0xcb64, 0xf9ff, 0xe876,
	        0x2102, 0x308b, 0x0210, 0x1399, 0x6726, 0x76af, 0x4434, 0x55bd,
	        0xad4a, 0xbcc3, 0x8e58, 0x9fd1, 0xeb6e, 0xfae7, 0xc87c, 0xd9f5,
	        0x3183, 0x200a, 0x1291, 0x0318, 0x77a7, 0x662e, 0x54b5, 0x453c,
	        0xbdcb, 0xac42, 0x9ed9, 0x8f50, 0xfbef, 0xea66, 0xd8fd, 0xc974,
	        0x4204, 0x538d, 0x6116, 0x709f, 0x0420, 0x15a9, 0x2732, 0x36bb,
	        0xce4c, 0xdfc5, 0xed5e, 0xfcd7, 0x8868, 0x99e1, 0xab7a, 0xbaf3,
	        0x5285, 0x430c, 0x7197, 0x601e, 0x14a1, 0x0528, 0x37b3, 0x263a,
	        0xdecd, 0xcf44, 0xfddf, 0xec56, 0x98e9, 0x8960, 0xbbfb, 0xaa72,
	        0x6306, 0x728f, 0x4014, 0x519d, 0x2522, 0x34ab, 0x0630, 0x17b9,
	        0xef4e, 0xfec7, 0xcc5c, 0xddd5, 0xa96a, 0xb8e3, 0x8a78, 0x9bf1,
	        0x7387, 0x620e, 0x5095, 0x411c, 0x35a3, 0x242a, 0x16b1, 0x0738,
	        0xffcf, 0xee46, 0xdcdd, 0xcd54, 0xb9eb, 0xa862, 0x9af9, 0x8b70,
	        0x8408, 0x9581, 0xa71a, 0xb693, 0xc22c, 0xd3a5, 0xe13e, 0xf0b7,
	        0x0840, 0x19c9, 0x2b52, 0x3adb, 0x4e64, 0x5fed, 0x6d76, 0x7cff,
	        0x9489, 0x8500, 0xb79b, 0xa612, 0xd2ad, 0xc324, 0xf1bf, 0xe036,
	        0x18c1, 0x0948, 0x3bd3, 0x2a5a, 0x5ee5, 0x4f6c, 0x7df7, 0x6c7e,
	        0xa50a, 0xb483, 0x8618, 0x9791, 0xe32e, 0xf2a7, 0xc03c, 0xd1b5,
	        0x2942, 0x38cb, 0x0a50, 0x1bd9, 0x6f66, 0x7eef, 0x4c74, 0x5dfd,
	        0xb58b, 0xa402, 0x9699, 0x8710, 0xf3af, 0xe226, 0xd0bd, 0xc134,
	        0x39c3, 0x284a, 0x1ad1, 0x0b58, 0x7fe7, 0x6e6e, 0x5cf5, 0x4d7c,
	        0xc60c, 0xd785, 0xe51e, 0xf497, 0x8028, 0x91a1, 0xa33a, 0xb2b3,
	        0x4a44, 0x5bcd, 0x6956, 0x78df, 0x0c60, 0x1de9, 0x2f72, 0x3efb,
	        0xd68d, 0xc704, 0xf59f, 0xe416, 0x90a9, 0x8120, 0xb3bb, 0xa232,
	        0x5ac5, 0x4b4c, 0x79d7, 0x685e, 0x1ce1, 0x0d68, 0x3ff3, 0x2e7a,
	        0xe70e, 0xf687, 0xc41c, 0xd595, 0xa12a, 0xb0a3, 0x8238, 0x93b1,
	        0x6b46, 0x7acf, 0x4854, 0x59dd, 0x2d62, 0x3ceb, 0x0e70, 0x1ff9,
	        0xf78f, 0xe606, 0xd49d, 0xc514, 0xb1ab, 0xa022, 0x92b9, 0x8330,
	        0x7bc7, 0x6a4e, 0x58d5, 0x495c, 0x3de3, 0x2c6a, 0x1ef1, 0x0f78,
	};

	public void setDirection(String d)
	{
		this.direction = d;
	}

	public byte[] bytesWithoutCRC()
	{
		int crc = CRC_CCITT_INIT_VAL;
		byte packet[] = new byte[MAX_FRAME_SIZE];
		int size = 0;
		int cpfSize = 1;
		int controlNumber = encodeControl(this.control);
		boolean uiOri = JSONArrayContains(control, "I") || JSONArrayContains(control, "UI");
		if (uiOri)
		{
			cpfSize = 2;
		} else {
			this.protocol = 0;
		}
		byte[] payloadArray = this.payload.getBytes(Charset.forName("US-ASCII"));
		int n = 7 + 7 + 7*path.length + cpfSize + payloadArray.length;
		byte[] bytes = new byte[n];
		
		int offset = 0;
		boolean sourceCBit = false;
		boolean destCBit = false;
		if (JSONArrayContains(control, "C")) {
			sourceCBit = false;
			destCBit = true;
		} else if (JSONArrayContains(control, "R")) {
			sourceCBit = true;
			destCBit = false;
		} else {
			sourceCBit = false;
			destCBit = false;
		}
		
		addCall(bytes, offset, this.destination, false, destCBit);
		offset += 7;
		addCall(bytes, offset, this.source, path==null || path.length==0, sourceCBit);
		offset += 7;
		for (int i=0; i < path.length; i++) 
		{
			addCall(bytes, offset, path[i], i==path.length-1, false);
			offset += 7;
		}
		
		bytes[offset++] = (byte) controlNumber;
		if (uiOri)
		{
			bytes[offset++] = (byte) protocol;
			
			for (int j=0; j<payloadArray.length; j++)
			{
				bytes[offset++] = payloadArray[j];
			}
		}
		
		assert (offset == n);
		assert (size == 0);

	    assert (crc == CRC_CCITT_INIT_VAL);
	    assert (bytes.length+2 <= packet.length);
	  
		for (int i=0; i < bytes.length; i++) {
			packet[size] = bytes[i];
			crc = crc_ccitt_update(crc, packet[size]);
			size++;
		}
		
	    int crcl = (crc & 0xff) ^ 0xff;
	    int crch = (crc >> 8) ^ 0xff;

	    packet[size] = (byte) crcl;
		crc = crc_ccitt_update(crc, packet[size]);
		size++;

	    packet[size] = (byte) crch;
		crc = crc_ccitt_update(crc, packet[size]);
		size++;
		
		assert (crc == AX25_CRC_CORRECT);
		return Arrays.copyOf(packet, size-2); // trim the checksum
	}
	
	// this constructor is used for sending packets from raw bytes
	public AX25Packet(byte[] bytes) 
	{
	    int crc = CRC_CCITT_INIT_VAL;
		byte packet[] = new byte[MAX_FRAME_SIZE];
		int size = 0;

		this.timestamp = new Date(System.currentTimeMillis());
		this.direction = null;
	  	assert (crc == CRC_CCITT_INIT_VAL);
	  	assert (bytes.length+2 <= packet.length);
	  
		for (int i=0; i<bytes.length; i++) {
			packet[size] = bytes[i];
			crc = crc_ccitt_update(crc, packet[size]);
			size++;
		}
		
	  	int crcl = (crc & 0xff) ^ 0xff;
	  	int crch = (crc >> 8) ^ 0xff;

	  	packet[size] = (byte) crcl;
		crc = crc_ccitt_update(crc, packet[size]);
		size++;

	  	packet[size] = (byte) crch;
		crc = crc_ccitt_update(crc, packet[size]);
		size++;
		
		assert (crc == AX25_CRC_CORRECT) : "Invalid CRC";
		int offset= 0;
		this.destination = parseCall(packet,offset);
		boolean destCBit = parseCallCBit(packet, offset);
		offset += 7;
		this.source = parseCall(packet,offset);
		boolean sourceCBit = parseCallCBit(packet, offset);
		offset += 7;
		
		String commandResponse = null;
		if (!sourceCBit && destCBit)
		{
			commandResponse = "C";
		} else if (sourceCBit && !destCBit) {
			commandResponse = "R";
		} else {
			commandResponse = null;
		}

		assert (!containsNonKeyboardChars(this.source) && !containsNonKeyboardChars(this.destination)) : "Bad characters in callsign";

		int repeaters = 0;
		while (offset+7 <= size && (packet[offset-1] & 0x01) == 0) {
			repeaters++;
			if (repeaters > 8) break; // missing LSB=1 to terminate the path
			String path_element = parseCall(packet,offset);
			offset += 7;
			if (path == null) {
				path = new String[1];
				path[0] = path_element;
			} else {
				path = Arrays.copyOf(path,path.length+1);
				path[path.length-1] = path_element;
			}
		}
		
		//offset += 2; // skip PID, control
        int controlNumber = ((int) packet[offset++]) & 0xff;
		byte[] payloadArray = new byte[0];
		this.control = decodeControl(commandResponse, controlNumber);
		if (this.controlContains("UI") || this.controlContains("I"))
		{
			this.protocol = ((int) packet[offset++]) & 0xff;

			if (size >= 18)
			{
				payloadArray = Arrays.copyOfRange(packet, offset, (size - 2)); // chop off CRC
			}
		} else {
			this.protocol = 0;
		}
		this.payload = new String(payloadArray);
	}

	public static AX25Packet buildResponse(AX25Packet packet, JSONArray control)
	{
		JSONObject reverse = new JSONObject();
		reverse.put("source", packet.getDestinationCallsign());
		reverse.put("destination", packet.getSourceCallsign());
		if (!JSONArrayContains(control, "R"))
			control.put("R");
		reverse.put("control", control);
		return new AX25Packet(reverse);
	}

    public static AX25Packet buildPacket(String source, String destination, String payload)
    {
        JSONObject packet = new JSONObject();
		packet.put("source", source);
		packet.put("destination", destination);
		packet.put("payload", payload);
		return new AX25Packet(packet);
    }
	
	public AX25Packet(JSONObject packet) 
    {
		this.direction = null;
		this.timestamp = new Date(packet.optLong("timestamp", System.currentTimeMillis()));
		this.payload =  packet.optString("payload", "");
		this.path = new String[] {};
		if (packet.has("path"))
		{
			List<Object> pathList = packet.getJSONArray("path").toList();
			this.path = pathList.toArray(new String[pathList.size()]);
		}
		this.control = packet.optJSONArray("control");
		if (this.control == null)
			this.control = decodeControl(null, AX25Packet.AX25_CONTROL_UI);
		this.protocol = packet.optInt("protocol", AX25Packet.AX25_PROTOCOL_NO_LAYER_3) & 0xff;
		this.commandResponse = packet.optString("commandResponse", null);
		this.source = packet.optString("source", "NOCALL").toUpperCase();
		this.destination = packet.optString("destination", "NOCALL").toUpperCase();
	}

	public JSONArray getControl()
	{
		return this.control;
	}

	public boolean controlContains(String key)
	{
		return JSONArrayContains(this.control, key);
	}
	
	public Date getTimestamp()
	{
		return this.timestamp;
	}

    public String getSourceCallsign()
    {
        return this.source;
    }

    public String getDestinationCallsign()
    {
        return this.destination;
    }

	public String[] getPath()
	{
		if (this.path != null)
			return this.path;
		else
			return new String[] {};
	}

    public String getPayload()
    {
		return this.payload;
    }

	public int getReceiveModulus()
	{
		ArrayList<String> RCV = new ArrayList<String>(Arrays.asList(new String[] {"R0","R1","R2","R3","R4","R5","R6","R7"}));
		for (int i = 0; i < this.control.length(); i++)
        {
            String item = this.control.optString(i, "");
			int rcvIndex = RCV.indexOf(item);
			if (rcvIndex >= 0)
				return rcvIndex;
		}
		return -1;
	}

	public int getSendModulus()
	{
		ArrayList<String> SND = new ArrayList<String>(Arrays.asList(new String[] {"S0","S1","S2","S3","S4","S5","S6","S7"}));
		for (int i = 0; i < this.control.length(); i++)
        {
            String item = this.control.optString(i, "");
			int sndIndex = SND.indexOf(item);
			if (sndIndex >= 0)
				return sndIndex;
		}
		return -1;
	}

	public void updatePayloadVar(String varName, long value)
	{
		updatePayloadVar(varName, String.valueOf(value));
	}

	public void updatePayloadVar(String varName, int value)
	{
		updatePayloadVar(varName, String.valueOf(value));
	}

	public void updatePayloadVar(String varName, String value)
	{
		if (this.payload !=null)
		{
			this.payload = this.payload.replaceAll(Pattern.quote(varName), value);
		}
	}
	
	private static String parseCall(byte[] packet, int offset) 
    {
		String call = "";
		int c, i;
		//int size = 0;
		
		for (i=0; i<6; i++) {
			c = (packet[offset+i] > 0) ? packet[offset+i] >> 1 : (packet[offset+i]+256) >> 1;
			//System.out.printf("Parsing byte %02x offset %d <%c>\n",c,offset+i,(char)c);
			if ((char) c != ' ')
				call += (char) c;
		}
		
		c = (packet[offset+i] > 0) ? packet[offset+i] >> 1 : (packet[offset+i]+256) >> 1;
		int ssid = c & 0x0f;
	  if (ssid != 0)
	  	call += String.format("-%d", ssid);
		
		return new String(call);
	}

	public static int encodeControl(JSONArray array)
	{
		ArrayList<String> RCV = new ArrayList<String>(Arrays.asList(new String[] {"R0","R1","R2","R3","R4","R5","R6","R7"}));
		ArrayList<String> SND = new ArrayList<String>(Arrays.asList(new String[] {"S0","S1","S2","S3","S4","S5","S6","S7"}));
		int control = 0;
		for (int i = 0; i < array.length(); i++)
        {
            String item = array.optString(i, "");
            if (item.equals("SABME"))
			{
				control = AX25_CONTROL_SABME;
			} else if (item.equals("SABM")) {
				control = AX25_CONTROL_SABM;
			} else if (item.equals("DISC")) {
				control = AX25_CONTROL_DISC;
			} else if (item.equals("UA")) {
				control = AX25_CONTROL_UA;
			} else if (item.equals("DM")) {
				control = AX25_CONTROL_DM;
			} else if (item.equals("FRMR")) {
				control = AX25_CONTROL_FRMR;
			} else if (item.equals("UI")) {
				control = AX25_CONTROL_UI;
			} else if (item.equals("RR")) {
				if (JSONArrayContains(array, "P"))
					control = 0b00010001;
				else 
					control = 0b00000001;
			} else if (item.equals("RNR")) {
				if (JSONArrayContains(array, "P"))
					control = 0b00010101;
				else
					control = 0b00000101;
			} else if (item.equals("REJ")) {
				if (JSONArrayContains(array, "P"))
					control = 0b00011001;
				else
					control = 0b00001001;
			} else if (item.equals("SREJ")) {
				if (JSONArrayContains(array, "P"))
					control = 0b00011101;
				else
					control = 0b00001101;
			} else if (item.equals("I")) {
				//control = 0b00000000;
				if (JSONArrayContains(array, "F"))
					control = 0b00000000;
				else
					control = 0b00010000;
			} else {
				int sndIndex = SND.indexOf(item);
				int rcvIndex = RCV.indexOf(item);
				if (rcvIndex >= 0)
				{
					control = ((rcvIndex & 0b111) << 5) | control;
				}
				if (sndIndex >= 0)
				{
					control = ((sndIndex & 0b111) << 1) | control;
				}
			}
        }
		return control;
	}

	public static JSONArray decodeControl(String cr, int controlNumber)
	{
		JSONArray ra = new JSONArray();
		if ((controlNumber & 0b11) == 0b01) // supervisory
		{
			int filtered = controlNumber & 0b1111;
			if (filtered == 0b0001)
			{
				ra.put("RR");
				int filtered_b = controlNumber & 0b11100000;
				ra.put("R" + String.valueOf((filtered_b >> 5) & 0b111 ));
			}
			if (filtered == 0b0101)
			{
				ra.put("RNR");
				int filtered_b = controlNumber & 0b11100000;
				ra.put("R" + String.valueOf((filtered_b >> 5) & 0b111 ));
			}
			if (filtered == 0b1001)
			{
				ra.put("REJ");
				int filtered_b = controlNumber & 0b11100000;
				ra.put("R" + String.valueOf((filtered_b >> 5) & 0b111 ));
			}
			if (filtered == 0b1101)
			{
				ra.put("SREJ");
				int filtered_b = controlNumber & 0b11100000;
				ra.put("R" + String.valueOf((filtered_b >> 5) & 0b111 ));
			}
			int pollFinal = (controlNumber & 0b00010000) >> 4;
			if (pollFinal == 1)
			{
				ra.put("P");
			} else {
				ra.put("F");
			}
		} else if ((controlNumber & 0b11) == 0b11) {
			int filtered = controlNumber & 0b11111111;
			if (filtered == AX25_CONTROL_SABME)
			{
				ra.put("SABME");
				ra.put("P");
			}
			if (filtered == AX25_CONTROL_SABM)
			{
				ra.put("SABM");
				ra.put("P");
			}
			if (filtered == AX25_CONTROL_DISC)
			{
				ra.put("DISC");
				ra.put("P");
			}
			if (filtered == AX25_CONTROL_DM)
			{
				ra.put("DM");
				ra.put("F");
			}
			if (filtered == AX25_CONTROL_FRMR)
			{
				ra.put("FRMR");
				ra.put("F");
			}
			if (filtered == AX25_CONTROL_UA)
			{
				ra.put("UA");
				ra.put("F");
			}
			if (filtered == AX25_CONTROL_UI)
			{
				ra.put("UI");
			}
		} else if ((controlNumber & 0b1) == 0b0) { // I frames
			ra.put("I");
			int pollFinal = (controlNumber & 0b00010000) >> 4;
			if (pollFinal == 1)
			{
				ra.put("P");
			} else {
				ra.put("F");
			}
			int filtered = controlNumber &   0b00001110;
			ra.put("S" + String.valueOf( (filtered >> 1) & 0b111 ));

			int filtered_b = controlNumber & 0b11100000;
			ra.put("R" + String.valueOf((filtered_b >> 5) & 0b111 ));
		}
		if (cr != null)
		{
			if (cr.toUpperCase().equals("C"))
			{
				ra.put("C");
			} else if (cr.toUpperCase().equals("R")) {
				ra.put("R");
			}
		}
		return ra;
	}

	private static boolean parseCallCBit(byte[] packet, int offset) 
    {
		int c, i = 6;
		c = (packet[offset+i] > 0) ? packet[offset+i] >> 1 : (packet[offset+i]+256) >> 1;	
		int cBit = c & 0xE0;
		return cBit == 96;
	}

	private static void addCall(byte[] bytes, int offset, String call, boolean last, boolean cBit)
    {
        int i;
        boolean call_ended = false;
        char c = ' ';
        int ssid = 0;

        for (i=0; i < 6; i++)
		{
            if (i < call.length())
                c = call.charAt(i);
            else
                call_ended = true;
            if (call_ended || !Character.isLetterOrDigit(c) || c=='-') {
                call_ended = true;
                c = ' ';
            } else c = Character.toUpperCase(c);
            bytes[offset++] = (byte) (c << 1);
        }

        for (i=0; i < call.length(); i++)
		{ 
            c = call.charAt(i);
            if (c=='-' && i+1<call.length()) 
			{
                ssid = Integer.parseInt(call.substring(i+1));
           		if (ssid > 15 || ssid < 0) ssid=0; // this is an error
            		break;
            }
        }

        /* The low-order bit of last call SSID should be set to 1 */
        ssid = (ssid << 1) | (cBit ? 0xE0 : 0x60) | (last ? 0x01 : 0);
        bytes[offset++] = (byte) ssid;
	}
	
    private static int crc_ccitt_update(int crc,byte b) 
    {
        return (crc >> 8) ^ crc_ccitt_tab[(crc ^ b) & 0xff];
    }

	private static boolean containsNonKeyboardChars(String str) {
		String keyboardChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`~!@#$%^&*()-_=+[{]}\\|;:'\",<.>/? ";
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (keyboardChars.indexOf(c) == -1) {
				return true;
			}
		}
		return false;
	}

	public JSONObject toJSONObject()
	{
		JSONObject ro = new JSONObject();
		ro.put("source", this.getSourceCallsign());
		ro.put("destination", this.getDestinationCallsign());
		if (this.payload != null)
		{
			if (payload.length() > 0)
				ro.put("payload", this.getPayload());
		}
		ro.put("control", this.control);
		if (this.protocol > 0)
			ro.put("protocol", this.protocol & 0xff);
		if (this.direction != null)
			ro.put("direction", this.direction);
		try
		{
			ro.put("size", this.bytesWithoutCRC().length);
		} catch (Exception x) {}
		if (this.path != null)
		{
			if (this.path.length > 0)
				ro.put("path", new JSONArray(this.path));
		}
		ro.put("timestamp", this.timestamp.getTime());
		return ro;
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

	public String toLogString()
	{
		return this.getSourceCallsign() + " > " + this.getDestinationCallsign() + ": " + this.getPayload();
	}
}