package io.micheal.debaterank;

import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

import java.net.URL;

public class SAXParseExceptionTournaments extends SAXParseException {
	public SAXParseExceptionTournaments(String message, Locator locator, URL url) {
		super(message + "\n" + url, locator);
	}
}
