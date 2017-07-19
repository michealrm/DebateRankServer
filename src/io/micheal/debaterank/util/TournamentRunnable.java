package io.micheal.debaterank.util;

import io.micheal.debaterank.Tournament;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public abstract class TournamentRunnable implements Runnable {

	public int tourn, event;
	public SAXParserFactory factory;
	public Tournament t;

	public TournamentRunnable(Tournament t, SAXParserFactory factory, int tourn, int event) {
		this.tourn = tourn;
		this.event = event;
		this.factory = factory;
		this.t = t;
	}

	public abstract void run();
}
