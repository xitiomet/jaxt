package org.openstatic.aprs.parser;

import java.util.ArrayList;

public class Parser
{    
    public static APRSPacket parse(final String packet) throws Exception 
	{
        int cs = packet.indexOf('>');
        String source = packet.substring(0,cs).toUpperCase();
        int ms = packet.indexOf(':');
        String digiList = packet.substring(cs+1,ms);
        String[] digiTemp = digiList.split(",");
        String dest = digiTemp[0].toUpperCase();
        ArrayList<Digipeater> digis = Digipeater.parseList(digiList, false);
        String body = packet.substring(ms+1);
        APRSPacket ap = parseBody(source, dest, digis, body);
        ap.setOriginalString(packet);
        return ap;
    }

    public static APRSPacket parseAX25(byte[] packet) throws Exception 
	{
	    int pos = 0;
	    String dest = new Callsign(packet, pos).toString();
	    pos += 7;
	    String source = new Callsign(packet, pos).toString();
	    pos += 7;
	    ArrayList<Digipeater> digis = new ArrayList<Digipeater>();
	    while ((packet[pos - 1] & 1) == 0) {
		    Digipeater d =new Digipeater(packet, pos);
		    digis.add(d);
		    pos += 7;
	    }
	    if (packet[pos] != 0x03 || packet[pos+1] != -16 /*0xf0*/)
		    throw new IllegalArgumentException("control + pid must be 0x03 0xF0!");
	    pos += 2;
	    String body = new String(packet, pos, packet.length - pos);
	    return parseBody(source, dest, digis, body);
    }

    public static APRSPacket parseBody(String source, String dest, ArrayList<Digipeater> digis, String body) throws Exception 
	{
        byte[] bodyBytes = body.getBytes();
        byte dti = bodyBytes[0];
        InformationField infoField = null;
        APRSTypes type = APRSTypes.T_UNSPECIFIED;
        boolean hasFault = false;
        switch ( dti ) {
        	case '!':
        	case '=':
        	case '/':
        	case '@':
        	case '`':
        	case '\'':
        	case '$':
        		if ( body.startsWith("$ULTW") ) {
        			// Ultimeter II weather packet
        		} else {
        			type = APRSTypes.T_POSITION;
        			infoField = new PositionPacket(bodyBytes,dest);
        		}
    			break;
        	case ':':
        		infoField = new MessagePacket(bodyBytes,dest);
        		break;
    		case ';':
    			if (bodyBytes.length > 29) {
    				//System.out.println("Parsing an OBJECT");
				type = APRSTypes.T_OBJECT;
    				infoField = new ObjectPacket(bodyBytes);
    			} else {
    				System.err.println("Object packet body too short for valid object");
    				hasFault = true; // too short for an object
    			}
    			break;
    		case '>':
    			type = APRSTypes.T_STATUS;
    			break;
    		case '<':
    			type = APRSTypes.T_STATCAPA;
    			break;
    		case '?':
    			type = APRSTypes.T_QUERY;
    			break;
    		case ')':
			type = APRSTypes.T_ITEM;
    			if (bodyBytes.length > 18) {
				infoField = new ItemPacket(bodyBytes);
    			} else {
    				hasFault = true; // too short
    			}
    			break;
    		case 'T':
    			if (bodyBytes.length > 18) {
    				//System.out.println("Parsing TELEMETRY");
    				//parseTelem(bodyBytes);
    			} else {
    				hasFault = true; // too short
    			}
    			break;
    		case '#': // Peet Bros U-II Weather Station
    		case '*': // Peet Bros U-II Weather Station
    		case '_': // Weather report without position
    			type = APRSTypes.T_WX;
    			break;
    		case '{':
    			type = APRSTypes.T_USERDEF;
    			break;
    		case '}': // 3rd-party
    			type = APRSTypes.T_THIRDPARTY;
    			break;

    		default:
    			hasFault = true; // UNKNOWN!
    			break;

        }
		if (infoField == null)
			infoField = new UnsupportedInfoField(bodyBytes);
        APRSPacket returnPacket = new APRSPacket(source,dest,digis,infoField);
        returnPacket.setType(type);
        returnPacket.setHasFault(hasFault);
        return returnPacket;
    }
    
}
