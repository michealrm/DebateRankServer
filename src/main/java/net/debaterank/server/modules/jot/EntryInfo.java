package net.debaterank.server.modules.jot;

import net.debaterank.server.entities.Tournament;
import org.jsoup.select.Elements;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

public class EntryInfo implements Serializable {

	public static class EventLinks implements Serializable {
		public String prelims, doubleOctas, bracket;

		public EventLinks(String p, String d, String b) {
			prelims = p;
			doubleOctas = d;
			bracket = b;
		}

		public String toString() {
			return prelims + " | " + doubleOctas + " | " + bracket;
		}
	}

	private transient Tournament tournament;
	private ArrayList<EventLinks> ldEventRows;
	private ArrayList<EventLinks> pfEventRows;
	private ArrayList<EventLinks> cxEventRows;

	public Tournament getTournament() {
		return tournament;
	}

	public void setTournament(Tournament tournament) {
		this.tournament = tournament;
	}

	public void addLdEventRow(EventLinks el) {
		ldEventRows.add(el);
	}

	public void addPfEventRow(EventLinks el) {
		pfEventRows.add(el);
	}

	public void addCxEventRow(EventLinks el) {
		cxEventRows.add(el);
	}

	public ArrayList<EventLinks> getLdEventRows() {
		return ldEventRows;
	}

	public ArrayList<EventLinks> getPfEventRows() {
		return pfEventRows;
	}

	public ArrayList<EventLinks> getCxEventRows() {
		return cxEventRows;
	}

	public EntryInfo(Tournament tournament) {
		this.tournament = tournament;
		this.ldEventRows = new ArrayList<>();
		this.pfEventRows = new ArrayList<>();
		this.cxEventRows = new ArrayList<>();
	}
}
