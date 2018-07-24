package net.debaterank.server.models;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity("tournaments")
public class Tournament {

	@Id
	private ObjectId id;
	private String name;
	private String link;
	private String state;

	public HashMap<String, Boolean> getRounds_contains() {
		return rounds_contains;
	}

	public void setRounds_contains(HashMap<String, Boolean> rounds_contains) {
		this.rounds_contains = rounds_contains;
	}

	public HashMap<String, Boolean> getRounds_exists() {
		return rounds_exists;
	}

	public void setRounds_exists(HashMap<String, Boolean> rounds_exists) {
		this.rounds_exists = rounds_exists;
	}

	private Date date;
	@Embedded
	private HashMap<String, Boolean> rounds_contains; // If an event is entered into the DB
	@Embedded
	private HashMap<String, Boolean> rounds_exists;
	@Embedded
	private List<LDRound> ld_rounds;
	@Embedded
	private List<PFRound> pf_rounds;
	@Embedded
	private List<CXRound> cx_rounds;

	public List<LDRound> getLd_rounds() {
		return ld_rounds;
	}

	public void setLd_rounds(List<LDRound> ld_rounds) {
		this.ld_rounds = ld_rounds;
	}

	public List<PFRound> getPf_rounds() {
		return pf_rounds;
	}

	public void setPf_rounds(List<PFRound> pf_rounds) {
		this.pf_rounds = pf_rounds;
	}

	public List<CXRound> getCx_rounds() {
		return cx_rounds;
	}

	public void setCx_rounds(List<CXRound> cx_rounds) {
		this.cx_rounds = cx_rounds;
	}

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public void replaceNull(Tournament tournament) {
		if(id == null)
			id = tournament.getId();
		if(name == null)
			name = tournament.getName();
		if(link == null)
			link = tournament.getLink();
		if(state == null)
			state = tournament.getState();
		if(date == null)
			date = tournament.getDate();
		if(ld_rounds == null)
			ld_rounds = tournament.getLd_rounds();
		if(pf_rounds == null)
			pf_rounds = tournament.getPf_rounds();
		if(cx_rounds == null)
			cx_rounds = tournament.getCx_rounds();
	}

	public Tournament(ObjectId id, String name, String link, String state, Date date, List<LDRound> ld_rounds, List<PFRound> pf_rounds, List<CXRound> cx_rounds) {
		this();
		this.id = id;
		this.name = name;
		this.link = link;
		this.state = state;
		this.date = date;
		this.ld_rounds = ld_rounds;
		this.pf_rounds = pf_rounds;
		this.cx_rounds = cx_rounds;
	}

	public Tournament(String name, String link, String state, Date date) {
		this();
		this.name = name;
		this.link = link;
		this.state = state;
		this.date = date;
	}

	public Tournament() {
		rounds_contains = new HashMap<>();
		rounds_contains.put("ld", false);
		rounds_contains.put("pf", false);
		rounds_contains.put("cx", false);

		rounds_exists = new HashMap<>();
		rounds_exists.put("ld", true);
		rounds_exists.put("pf", true);
		rounds_exists.put("cx", true);
	}

}
