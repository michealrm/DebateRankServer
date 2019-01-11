package net.debaterank.server.modules.jot;

import net.debaterank.server.entities.Tournament;
import org.jsoup.select.Elements;

import java.io.Serializable;
import java.util.Objects;

public class EntryInfo implements Serializable {
	private Tournament tournament;
	private transient Elements ldEventRows;
	private transient Elements pfEventRows;
	private transient Elements cxEventRows;

	// Used to writing to file
	private String ldERStr;
	private String pfERStr;
	private String cxERStr;

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

	public String getLDERStr() {
		return ldERStr;
	}

	public void setLDERStr(String ldERStr) {
		this.ldERStr = ldERStr;
	}

	public String getPFERStr() {
		return pfERStr;
	}

	public void setPFERStr(String pfERStr) {
		this.pfERStr = pfERStr;
	}

	public String getCXERStr() {
		return cxERStr;
	}

	public void setCXERStr(String cxERStr) {
		this.cxERStr = cxERStr;
	}

	public EntryInfo(Tournament tournament, Elements ldEventRows, Elements pfEventRows, Elements cxEventRows) {
		this.tournament = tournament;
		this.ldEventRows = ldEventRows;
		this.pfEventRows = pfEventRows;
		this.cxEventRows = cxEventRows;
	}
}
