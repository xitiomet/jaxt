package org.openstatic.aprs.parser;

public class UnparsablePositionException extends Exception
{
	private static final long serialVersionUID = 1L;

	public UnparsablePositionException(String ex) {
		super(ex);
	}
}
