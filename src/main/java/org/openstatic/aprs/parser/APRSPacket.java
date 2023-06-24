package org.openstatic.aprs.parser;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class APRSPacket implements Serializable 
{
    private static final long serialVersionUID = 1L;
    private String originalString;
	private String sourceCall;
    private String destinationCall;
    private ArrayList<Digipeater> digipeaters;
    private char dti;
    private InformationField aprsInformation;
    protected boolean hasFault;
    private APRSTypes type;

    static final String REGEX_PATH_ALIASES = "^(WIDE|TRACE|RELAY)\\d*$";
    
    public APRSPacket( String source, String destination, ArrayList<Digipeater> digipeaters, InformationField info)
	{
        this.sourceCall=source.toUpperCase();
        this.destinationCall=destination.toUpperCase();
        if ( digipeaters == null ) {
        	Digipeater aprsIs = new Digipeater("TCPIP*");
        	this.digipeaters = new ArrayList<Digipeater>();
        	this.digipeaters.add(aprsIs);
        } else {
        	this.digipeaters = digipeaters;
        }
        this.aprsInformation = info;
        if ( info != null ) {
            this.dti = aprsInformation.getDateTypeIdentifier();
        } else {
            this.dti= (char)' ';
        }
    }
    
    public static final String getBaseCall(String callsign)
	{
    	int sepIdx = callsign.indexOf('-');
    	if ( sepIdx > -1 ) {
    		return callsign.substring(0,sepIdx);
    	} else {
    		return callsign;
    	}
    }
    
    public static final String getSsid(String callsign)
	{
    	int sepIdx = callsign.indexOf('-');
    	if ( sepIdx > -1 ) {
    		return callsign.substring(sepIdx+1);
    	} else {
    		return "0";
    	}
    }
    
    public String getIgate()
	{
    	for ( int i=0; i<digipeaters.size(); i++) {
    		Digipeater d = digipeaters.get(i);
    		if ( d.getCallsign().equalsIgnoreCase("qar") && i<digipeaters.size()-1 ) {
    			return digipeaters.get(i+1).toString();
    		}
    		if ( d.getCallsign().equalsIgnoreCase("qas") && i<digipeaters.size()-1 ) {
    			return digipeaters.get(i+1).toString();
    		}
    		if ( d.getCallsign().equalsIgnoreCase("qac") && i<digipeaters.size()-1 ) {
    			return digipeaters.get(i+1).toString();
    		}
    		if ( d.getCallsign().equalsIgnoreCase("qao") && i<digipeaters.size()-1 ) {
    			return digipeaters.get(i+1).toString();
    		}
    	}
    	return "";
    }

    public String getSourceCall()
	{
        return sourceCall;
    }

    public String getDestinationCall()
	{
        return destinationCall;
    }

    public ArrayList<Digipeater> getDigipeaters()
	{
        return digipeaters;
    }
    
    public void setDigipeaters(ArrayList<Digipeater> newDigis)
	{
	    digipeaters = newDigis;
    }

    public String getLastUsedDigi()
	{
        for (int i=digipeaters.size()-1; i>=0; i--) {
            Digipeater d = digipeaters.get(i);
	    String call = d.getCallsign();
            if (d.isUsed() && !call.matches(REGEX_PATH_ALIASES))
                return call;
        }
        return null;
    }

    public String getDigiString()
	{
        StringBuilder sb = new StringBuilder();
        for ( Digipeater digi : digipeaters ) {
            sb.append(","+digi.toString());
        }
        return sb.toString();
    }

    public char getDti()
	{
        return dti;
    }

    public InformationField getAprsInformation() {
        return aprsInformation;
    }
    public boolean isAprs()
	{
    	return true;
    }

	public boolean hasFault()
	{
		return hasFault;
	}

	public void setHasFault(boolean hasFault)
	{
		this.hasFault = hasFault;
	}

	public APRSTypes getType() {
		return type;
	}

	public void setType(APRSTypes type)
	{
		this.type = type;
	}

	public final String getOriginalString()
	{
		return originalString;
	}

	public final void setOriginalString(String originalString)
	{
		this.originalString = originalString;
	}

	@Override
	public String toString() 
	{
		return sourceCall+">"+destinationCall+getDigiString()+":"+aprsInformation.toString();
	}

	public byte[] toAX25Frame() throws IllegalArgumentException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] dest = new Digipeater(destinationCall + "*").toAX25();
		baos.write(dest, 0, dest.length);
		byte[] src = new Digipeater(sourceCall).toAX25();
		if (digipeaters.size() == 0)
			src[6] |= 1;
		baos.write(src, 0, src.length);
		for (int i = 0; i < digipeaters.size(); i++) {
			byte[] d = digipeaters.get(i).toAX25();
			if (i == digipeaters.size() - 1)
				d[6] |= 1;
			baos.write(d, 0, 7);
		}
		baos.write(0x03);
		baos.write(0xF0);
		byte[] content = aprsInformation.getRawBytes();
		baos.write(content, 0, content.length);
		return baos.toByteArray();
	}
}

