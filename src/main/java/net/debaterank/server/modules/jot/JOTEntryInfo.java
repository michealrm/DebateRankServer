package net.debaterank.server.modules.jot;

import net.debaterank.server.models.Tournament;
import org.jsoup.select.Elements;

public class JOTEntryInfo {
	private Tournament tournament;
	private Elements eventRows;

	public Tournament getTournament() {
		return tournament;
	}

	public void setTournament(Tournament tournament) {
		this.tournament = tournament;
	}

	public Elements getEventRows() {
		return eventRows;
	}

	public void setEventRows(Elements eventRows) {
		this.eventRows = eventRows;
	}

	public JOTEntryInfo(Tournament tournament, Elements eventRows) {
		this.tournament = tournament;
		this.eventRows = eventRows;
	}
}
