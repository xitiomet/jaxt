package org.openstatic.aprs.parser;

import java.io.Serializable;

public class MessagePacket extends InformationField implements Serializable
{
	private static final long serialVersionUID = 1L;
    private String messageBody;
    private String messageNumber;
    private String targetCallsign ="";
    private boolean isAck = false;
    private boolean isRej = false;
    
    public MessagePacket( byte[] bodyBytes, String destCall ) 
    {
        super(bodyBytes);
        String message = new String(bodyBytes);
        if ( message.length() < 2) {
            this.hasFault = true;
            return;
        }
        int msgSpc = message.indexOf(':', 2);
        if ( msgSpc < 1 ) {
        	this.targetCallsign = "UNKNOWN";
        } else {
        	targetCallsign = message.substring(1,msgSpc).trim().toUpperCase();
        }
        int msgNumberIdx = message.lastIndexOf('{');
        this.messageNumber="";
        if ( msgNumberIdx > -1 ) {
            this.messageNumber = message.substring(msgNumberIdx+1);
            messageBody = message.substring(11,msgNumberIdx);
        } else {
            messageBody = message.substring(11);
        }
        String lcMsg = messageBody.toLowerCase();
        if ( lcMsg.startsWith("ack") ) {
        	isAck = true;
        	this.messageNumber = messageBody.substring(3,messageBody.length());
		this.messageBody = messageBody.substring(0, 3);
        }
        if ( lcMsg.startsWith("rej") ) {
        	isRej = true;
        	this.messageNumber = messageBody.substring(3,messageBody.length());
		this.messageBody = messageBody.substring(0, 3);
        }
    }
    
    public MessagePacket(String targetCallsign, String messageBody, String messageNumber)
    {
    	this.messageBody = messageBody;
    	this.targetCallsign = targetCallsign;
    	this.messageNumber = messageNumber;
    	if ( messageBody.equals("ack") ) isAck = true;
    	if ( messageBody.equals("rej") ) isRej = true;
    	super.setDataTypeIdentifier(':');
    	super.type=APRSTypes.T_MESSAGE;
    }
    
    public String getMessageBody()
    {
        return this.messageBody;
    }

    public void setMessageBody(String messageBody)
    {
        this.messageBody = messageBody;
    }

    public String getMessageNumber()
    {
        return messageNumber;
    }

    public void setMessageNumber(String messageNumber) 
    {
        this.messageNumber = messageNumber;
    }

    public String getTargetCallsign()
    {
        return targetCallsign;
    }

    public void setTargetCallsign(String targetCallsign)
    {
        this.targetCallsign = targetCallsign;
    }

	public boolean isAck()
    {
		return isAck;
	}

	public void setAck(boolean isAck)
    {
		this.isAck = isAck;
	}

	public boolean isRej()
    {
		return isRej;
	}

	public void setRej(boolean isRej)
    {
		this.isRej = isRej;
	}

	@Override
	public String toString()
    {
		if (rawBytes != null)
			return new String(rawBytes);
		if ( this.messageBody.equals("ack") || this.messageBody.equals("rej")) {
			return String.format(":%-9s:%s%s", this.targetCallsign, this.messageBody, this.messageNumber);
		} else if (messageNumber.length() > 0) {
			return String.format(":%-9s:%s{%s", this.targetCallsign, this.messageBody, this.messageNumber);
		} else {
			return String.format(":%-9s:%s", this.targetCallsign, this.messageBody);
		}
	}
}
