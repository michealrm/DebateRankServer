package net.debaterank.server.modules.jot;

import net.debaterank.server.entities.Tournament;
import org.jsoup.select.Elements;

import java.util.Objects;

public class EntryInfo {
	private Tournament tournament;
	private Elements ldEventRows;
	private Elements pfEventRows;
	private Elements cxEventRows;

	public Tournament getTournament() {
		return tournament;
	}

	public void setTournament(Tournament tournament) {
		this.tournament = tournament;
	}

	public Elements getLdEventRows() {
		return ldEventRows;
	}

	public void setLdEventRows(Elements ldEventRows) {
		this.ldEventRows = ldEventRows;
	}

	public Elements getPfEventRows() {
		return pfEventRows;
	}

	public void setPfEventRows(Elements pfEventRows) {
		this.pfEventRows = pfEventRows;
	}

	public Elements getCxEventRows() {
		return cxEventRows;
	}

	public void setCxEventRows(Elements cxEventRows) {
		this.cxEventRows = cxEventRows;
	}

	public EntryInfo(Tournament tournament, Elements ldEventRows, Elements pfEventRows, Elements cxEventRows) {
		this.tournament = tournament;
		this.ldEventRows = ldEventRows;
		this.pfEventRows = pfEventRows;
		this.cxEventRows = cxEventRows;
	}
}
