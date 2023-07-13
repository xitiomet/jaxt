package org.openstatic.aprs.parser;

public class PositionPacket extends InformationField implements java.io.Serializable
{
	private static final long serialVersionUID = 1L;
	private Position position;
	private String positionSource;
	private boolean compressedFormat;

	public PositionPacket(byte[] msgBody, String destinationField) throws Exception 
	{
		super(msgBody);
		positionSource = "Unknown";
		char packetType = (char) msgBody[0];
		int cursor = 0;
		switch (packetType) {
		case '\'' :
		case '`':
			type = APRSTypes.T_POSITION;
			position = PositionParser.parseMICe(msgBody, destinationField);
			this.extension = PositionParser.parseMICeExtension(msgBody, destinationField);
			positionSource = "MICe";
			cursor = 10;
			if (cursor < msgBody.length && (msgBody[cursor] == '>' || msgBody[cursor] == ']' || msgBody[cursor] == '`'))
				cursor++;
			if (cursor < msgBody.length && msgBody[cursor] == '"')
				cursor += 4;
			break;
		case '!':
			if (msgBody[1] == 'U' && msgBody[2] == 'L' && msgBody[3] == 'T')
			{
				type = APRSTypes.T_WX;
				break;
			}
		case '=':
		case '/':
		case '@':
			if (msgBody.length < 10) {
				hasFault = true;
			} else {
				type = APRSTypes.T_POSITION;
				cursor = 1;

				if (packetType == '/' || packetType == '@') {
					cursor += 7;
				}
				char posChar = (char) msgBody[cursor];
				if (validSymTableCompressed(posChar))
				{
					position = PositionParser.parseCompressed(msgBody, cursor);
					this.extension = PositionParser.parseCompressedExtension(msgBody, cursor);
					positionSource = "Compressed";
					cursor += 13;
				} else if ('0' <= posChar && posChar <= '9') {
					position = PositionParser.parseUncompressed(msgBody);
					try {
						this.extension = PositionParser.parseUncompressedExtension(msgBody, cursor);
					} catch (ArrayIndexOutOfBoundsException oobex) {
						this.extension = null;
					}
					positionSource = "Uncompressed";
					cursor += 19;
				} else {
					hasFault = true;
				}
				break;
			}
		case '$':
			if (msgBody.length > 10) {
				type = APRSTypes.T_POSITION;
				position = PositionParser.parseNMEA(msgBody);
				positionSource = "NMEA";
			} else {
				hasFault = true;
			}
			break;

		}
		if (cursor > 0 && cursor < msgBody.length)
		{
			comment = new String(msgBody, cursor, msgBody.length - cursor, "UTF-8");
		}
		compressedFormat = false;
	}
	
	public PositionPacket(Position position, String comment) 
	{
		this.position = position;
		this.type = APRSTypes.T_POSITION;
		this.comment = comment;
		compressedFormat = false;
	}

	public PositionPacket(Position position, String comment, boolean msgCapable)
	{
		this(position, comment);
		canMessage = msgCapable;
	}

	public void setCompressedFormat(boolean val)
	{
		compressedFormat = val;
	}

	public boolean getCompressedFormat()
	{
		return compressedFormat;
	}

	private boolean validSymTableCompressed(char c) 
	{
		if (c == '/' || c == '\\')
			return true;
		if ('A' <= c && c <= 'Z')
			return true;
		if ('a' <= c && c <= 'j')
			return true;
		return false;
	}

	public Position getPosition() 
	{
		return position;
	}

	public void setPosition(Position position) 
	{
		this.position = position;
	}

	@Override
	public String toString()
	{
		if (rawBytes != null)
			return new String(rawBytes);
		if (compressedFormat)
			return (canMessage ? "=" : "!") + position.toCompressedString() + comment;
		return (canMessage ? "=" : "!") + position + comment;
	}

	public String getPositionSource() 
	{
		return positionSource;
	}
}
