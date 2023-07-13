package org.openstatic.aprs.parser;

import java.io.Serializable;

public class RangeExtension extends DataExtension implements Serializable
{
	private static final long serialVersionUID = 1L;
	private int range;
	
	public RangeExtension( int range )
	{
		this.setRange(range);
	}

	public void setRange(int range)
	{
		this.range = range;
	}

	public int getRange() 
	{
		return range;
	}
	
	@Override
	public String toSAEString() 
	{
		return "Range of "+range+" miles";
	}

}
