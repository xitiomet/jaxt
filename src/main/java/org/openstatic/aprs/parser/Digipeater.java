package org.openstatic.aprs.parser;

import java.io.Serializable;
import java.util.ArrayList;

public class Digipeater extends Callsign implements Serializable 
{
	private static final long serialVersionUID = 1L;
    private boolean used;
    
    public Digipeater(String call)
    {
        super(call.replaceAll("\\*", ""));
        if ( call.indexOf("*") >= 0 ) 
        {
            setUsed(true);
        }
    }
    
    public Digipeater(byte[] data, int offset)
    {
	    super(data, offset);
	    this.used = (data[offset + 6] & 0x80) == 0x80;
    }

    public static ArrayList<Digipeater> parseList(String digiList, boolean includeFirst)
    {
        String[] digiTemp = digiList.split(",");
        ArrayList<Digipeater> digis = new ArrayList<Digipeater>();
        boolean includeNext = includeFirst;
        for (String digi : digiTemp) {
            String digiTrim = digi.trim();
            if (digiTrim.length() > 0 && includeNext)
                digis.add(new Digipeater(digiTrim));
            includeNext = true;
        }
        return digis;
    }

    public boolean isUsed()
    {
        return used;
    }

    public void setUsed(boolean used)
    {
        this.used = used;
    }
    
    @Override
    public String toString() 
    {
        return super.toString() + ( isUsed() ? "*":"");
    }

    @Override
    public byte[] toAX25() throws IllegalArgumentException
    {
        byte[] ax25 = super.toAX25();
	    ax25[6] |= (isUsed()?0x80:0);
	    return ax25;
    }
}
