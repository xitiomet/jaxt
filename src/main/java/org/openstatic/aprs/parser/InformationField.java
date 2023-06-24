package org.openstatic.aprs.parser;

import java.io.Serializable;

public abstract class InformationField implements Serializable
{
	private static final long serialVersionUID = 1L;
	private char dataTypeIdentifier;
    protected byte[] rawBytes;
    protected APRSTypes type;
	protected boolean hasFault = false;
    protected boolean canMessage = false;
    DataExtension extension = null;
	protected String comment = "";

    public InformationField()
    {
    }
    
    public InformationField( byte[] rawBytes ) 
    {
        if ( rawBytes.length < 1 ) {
            System.err.println("Parse error:  zero length information field");
        }
        this.rawBytes = rawBytes;
        this.dataTypeIdentifier = (char)rawBytes[0];
        switch ( dataTypeIdentifier ) {
        	case '@' :
        	case '=' :
        	case '\'':
        	case ':' : this.canMessage = true;
        }
    }
    
    public char getDateTypeIdentifier() {
        return dataTypeIdentifier;
    }

    public void setDataTypeIdentifier(char dti) {
        this.dataTypeIdentifier = dti;
    }

    public byte[] getRawBytes() 
    {
        if (rawBytes != null)
            return rawBytes;
        else
            return toString().getBytes();
    }
    
    public byte[] getBytes(int start, int end) 
    {
        byte[] returnArray = new byte[end-start];
        System.arraycopy(getRawBytes(), start, returnArray, 0, end-start);
        return returnArray;
    }
    
    public String getComment()
    {
        return comment;
    }
    
    @Override
    public String toString()
    {
        return new String(rawBytes);
    }

	public boolean isHasFault()
    {
		return hasFault;
	}

	public final DataExtension getExtension()
    {
		return extension;
	}
}
