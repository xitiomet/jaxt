package org.openstatic.aprs.parser;

import java.io.Serializable;

public class ObjectPacket extends InformationField implements Serializable
{
	private static final long serialVersionUID = 1L;
	protected String objectName;
	protected boolean live = true;
	protected Position position;

	protected ObjectPacket() 
	{
	}

	public ObjectPacket(byte[] msgBody) throws Exception
	{
		super(msgBody);
		this.objectName = new String(msgBody, 1, 9).trim();
		this.live = (msgBody[10] == '*');
		int cursor = 18;
		if ( msgBody[cursor] > '0' && msgBody[cursor] < '9' ) {
		    this.position = PositionParser.parseUncompressed(msgBody, cursor);
		    cursor += 19;
		} else {
		    this.position = PositionParser.parseCompressed(msgBody, cursor);
		    cursor += 12;
		}
		comment = new String(msgBody, cursor, msgBody.length - cursor, "UTF-8").trim();
	}

	public ObjectPacket( String objectName, boolean live, Position position, String comment)
	{
		this.objectName = objectName;
		this.live = live;
		this.position = position;
		this.comment = comment;
	}

	public String getObjectName() 
	{
		return objectName;
	}

	public void setObjectName(String objectName)
	{
		this.objectName = objectName;
	}

	public boolean isLive()
	{
		return live;
	}

	public void setLive(boolean live)
	{
		this.live = live;
	}

	public Position getPosition()
	{
		return position;
	}

	public void setPosition(Position position)
	{
		this.position = position;
	}

	public void setExtension(DataExtension extension)
	{
		this.extension = extension;
	}

	@Override
	public String toString() 
	{
		if (rawBytes != null)
			return new String(rawBytes);
		return String.format(";%-9s%c%s%s", this.objectName, live ? '*':'_', position.toString(), comment);
	}
}
