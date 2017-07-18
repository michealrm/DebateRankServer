package io.micheal.debaterank.util;

import io.micheal.debaterank.Tournament;

import javax.xml.parsers.SAXParser;

public abstract class TournamentRunnable implements Runnable {

	public int tourn, event;
	public SAXParser saxParser;

	public TournamentRunnable(SAXParser saxParser, int tourn, int event) {
		this.tourn = tourn;
		this.event = event;
		this.saxParser = saxParser;
	}

	public abstract void run();
}
