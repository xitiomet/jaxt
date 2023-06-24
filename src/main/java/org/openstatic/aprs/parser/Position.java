package org.openstatic.aprs.parser;

import java.util.Date;
import java.util.Locale;

public class Position implements java.io.Serializable
{
	private static final long serialVersionUID = 1L;
	private Double latitude = 0d, longitude = 0d;
	private Integer altitude = -1;
	private Integer positionAmbiguity;
	private Date timestamp;
	private char symbolTable, symbolCode;
	private String csTField = " sT";

	public Position() {
	    timestamp = new Date();
	}
	
	public Position(double lat, double lon, int posAmb, char st, char sc) {
		this.latitude = Math.round(lat * 100000) * 0.00001D;
		this.longitude = Math.round(lon * 100000) * 0.00001D;
		this.positionAmbiguity = posAmb;
		this.symbolTable = st;
		this.symbolCode = sc;
		this.timestamp = new Date();
	}
	
	public Position(double lat, double lon) {
		this.latitude = Math.round(lat * 100000) * 0.00001D;
		this.longitude = Math.round(lon * 100000) * 0.00001D;
		this.positionAmbiguity=0;
		this.symbolTable = '\\';
		this.symbolCode = '.';
		this.timestamp = new Date();
	}

	public double getLatitude()
	{
		return latitude;
	}

	public void setLatitude(double latitude)
	{
		this.latitude = latitude;
	}

	public double getLongitude()
	{
		return longitude;
	}

	public void setLongitude(double longitude)
	{
		this.longitude = longitude;
	}

	public int getAltitude()
	{
		return altitude;
	}

	public void setAltitude(int altitude)
	{
		this.altitude = altitude;
	}

	public int getPositionAmbiguity()
	{
		return positionAmbiguity;
	}

	public void setPositionAmbiguity(int positionAmbiguity)
	{
		this.positionAmbiguity = positionAmbiguity;
	}

	public Date getTimestamp()
	{
		return this.timestamp;
	}

	public void setTimestamp(Date timestamp)
	{
		this.timestamp = timestamp;
	}

	public char getSymbolTable()
	{
		return symbolTable;
	}

	public void setSymbolTable(char symbolTable)
	{
		this.symbolTable = symbolTable;
	}

	public char getSymbolCode()
	{
		return symbolCode;
	}

	public void setSymbolCode(char symbolCode)
	{
		this.symbolCode = symbolCode;
	}
	
	public String getDMS(double decimalDegree, boolean isLatitude) 
	{
		int minFrac = (int)Math.round(decimalDegree*6000);
		boolean negative = (minFrac < 0);
		if (negative)
				minFrac = -minFrac;
		int deg = minFrac / 6000;
		int min = (minFrac / 100) % 60;
		minFrac = minFrac % 100;
		String ambiguousFrac;

		switch (positionAmbiguity) {
		case 1: // "dd  .  N"
			ambiguousFrac = "  .  "; break;
		case 2: // "ddm .  N"
			ambiguousFrac = String.format((Locale)null, "%d .  ", min/10); break;
		case 3: // "ddmm.  N"
			ambiguousFrac = String.format((Locale)null, "%02d.  ", min); break;
		case 4: // "ddmm.f N"
			ambiguousFrac = String.format((Locale)null, "%02d.%d ", min, minFrac/10); break;
		default: // "ddmm.ffN"
			ambiguousFrac = String.format((Locale)null, "%02d.%02d", min, minFrac); break;
		}
		if ( isLatitude ) {
			return String.format((Locale)null, "%02d%s%s", deg, ambiguousFrac, ( negative ? "S" : "N"));
		} else {
			return String.format((Locale)null, "%03d%s%s", deg, ambiguousFrac, ( negative ? "W" : "E"));
		}
	}
	
	@Override
	public String toString() 
	{
		return getDMS(latitude,true)+symbolTable+getDMS(longitude,false)+symbolCode;
	}
	
	public String toDecimalString()
	{
		return latitude+", "+longitude;
	}

	public void setCsTField(String val) 
	{
		if(val == null || val == "") {
			val = " sT";
		}
		csTField = val;
	}

	public String getCsTField()
	{
		return csTField;
	}

	public String toCompressedString()
	{
		long latbase = Math.round(380926 * (90-this.latitude));
		long latchar1 = latbase / (91*91*91)+33;
		latbase = latbase % (91*91*91);
		long latchar2 = latbase / (91*91)+33;
		latbase = latbase % (91*91);
		int latchar3 = (int)(latbase / 91)+33;
		int latchar4 = (int)(latbase % 91)+33;
		long lonbase = Math.round(190463 * (180+this.longitude));
		long lonchar1 = lonbase / (91*91*91)+33;
		lonbase %= (91*91*91);
		long lonchar2 = lonbase / (91*91)+33;
		lonbase = lonbase % (91*91);
		int lonchar3 = (int)(lonbase / 91)+33;
		int lonchar4 = (int)(lonbase % 91)+33;
		
		return ""+symbolTable+(char)latchar1+(char)latchar2+(char)latchar3+(char)latchar4+
				""+(char)lonchar1+(char)lonchar2+(char)lonchar3+(char)lonchar4+symbolCode+csTField;
	}

	public static float distFrom(double lat1, double lng1, double lat2, double lng2)
	{
		double earthRadius = 3958.75;
		double dLat = Math.toRadians(lat2-lat1);
		double dLng = Math.toRadians(lng2-lng1);
		double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
					Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
					Math.sin(dLng/2) * Math.sin(dLng/2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		double dist = earthRadius * c;

		return new Float(dist).floatValue();
    }
	
	public float distance(Position position2)
	{
		double lat1 = this.getLatitude();
		double lat2 = position2.getLatitude();
		double lng1 = this.getLongitude();
		double lng2 = position2.getLongitude();
		return distFrom(lat1,lng1,lat2,lng2);
	}
	
	public float direction(Position position2)
	{
		double Lat1 = Math.toRadians(position2.getLatitude());
		double Lon1 = position2.getLongitude();
		double Lat2 = Math.toRadians(getLatitude());
		double Lon2 = getLongitude();
		double dLon = Math.toRadians(Lon2 - Lon1);
		double y = Math.sin(dLon) * Math.cos(Lat2);
		double x = Math.cos(Lat1) * Math.sin(Lat2) - Math.sin(Lat1) *
			Math.cos(Lat2) * Math.cos(dLon);
		return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
	}
}
