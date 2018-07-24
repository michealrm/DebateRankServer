package net.debaterank.server.modules.tabroom;

import net.debaterank.server.models.Tournament;

public class TabroomEntryInfo {

	private Tournament tournament;
	private int tourn_id;
	private int event_id;
	private String endpoint;

	public Tournament getTournament() {
		return tournament;
	}

	public void setTournament(Tournament tournament) {
		this.tournament = tournament;
	}

	public int getTourn_id() {
		return tourn_id;
	}

	public void setTourn_id(int tourn_id) {
		this.tourn_id = tourn_id;
	}

	public int getEvent_id() {
		return event_id;
	}

	public void setEvent_id(int event_id) {
		this.event_id = event_id;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public TabroomEntryInfo(Tournament tournament, int tourn_id, int event_id, String endpoint) {

		this.tournament = tournament;
		this.tourn_id = tourn_id;
		this.event_id = event_id;
		this.endpoint = endpoint;
	}
}
