
package org.openstatic.aprs.parser;

import java.io.Serializable;

public class PHGExtension extends DataExtension implements Serializable 
{
	private static final long serialVersionUID = 1L;
	private static int[] powerCodes = {0,1,4,9,16,25,36,49,64,81};
	private static int[] heightCodes = {10,20,40,80,160,320,640,1280,2560,5120};
	private static int[] gainCodes = {0,1,2,3,4,5,6,7,8,9};
	private static int[] directivityCodes = {0,45,90,135,180,225,270,315,360,0};
	
	private int power;
	private int height;
	private int gain;
	private int directivity;

	public int getPower()
	{
		return power;
	}

	public void setPower(int power)
	{
		this.power = powerCodes[power];
	}

	public int getHeight()
	{
		return height;
	}

	public void setHeight(int height)
	{
		this.height = heightCodes[height];
	}

	public int getGain() 
	{
		return gain;
	}
	
	public void setGain(int gain)
	{
		this.gain = gainCodes[gain];
	}

	public int getDirectivity() 
	{
		return directivity;
	}

	public void setDirectivity(int directivity)
	{
		this.directivity = directivityCodes[directivity];
	}
	
	@Override
	public String toSAEString() {
		return power+" watts at "+height+" ft HAAT with "+gain+" dBi gain directed at "+directivity+" degress";
	}
}
