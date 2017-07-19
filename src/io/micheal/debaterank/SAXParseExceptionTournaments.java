package io.micheal.debaterank;

import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

import java.net.URL;

public class SAXParseExceptionTournaments extends SAXParseException {
	public SAXParseExceptionTournaments(String url, String message, String publicID, String systemID, int lineNumber, int columnNumber) {
		super(url + "\n" + message, publicID, systemID, lineNumber, columnNumber);
	}
}
