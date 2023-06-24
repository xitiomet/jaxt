package org.openstatic.aprs.parser;

public class UnsupportedInfoField extends InformationField
{
    private static final long serialVersionUID = 1L;

    public UnsupportedInfoField() {
		super();
	}

	public UnsupportedInfoField(byte[] rawBytes) {
		super(rawBytes);
	}
}
