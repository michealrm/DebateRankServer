package net.debaterank.server.models;

import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Property;
import org.mongodb.morphia.annotations.Reference;

@Entity("ballots")
public class Ballot {
	@Reference
	private Round round;
	@Reference
	private Judge judge;
	@Property("aff1_speaks")
	private Double aff1_speaks;
	@Property("aff2_speaks")
	private Double aff2_speaks;
	@Property("neg1_speaks")
	private Double neg1_speaks;
	@Property("neg2_speaks")
	private Double neg2_speaks;
	@Property("aff1_place")
	private Integer aff1_place;
	@Property("aff2_place")
	private Integer aff2_place;
	@Property("neg1_place")
	private Integer neg1_place;
	@Property("neg2_place")
	private Integer neg2_place;
	@Property("decision")
	private String decision;

	public Judge getJudge() {
		return judge;
	}

	public void setJudge(Judge judge) {
		this.judge = judge;
	}

	public Double getAff1_speaks() {
		return aff1_speaks;
	}

	public void setAff1_speaks(Double aff1_speaks) {
		this.aff1_speaks = aff1_speaks;
	}

	public Double getAff2_speaks() {
		return aff2_speaks;
	}

	public void setAff2_speaks(Double aff2_speaks) {
		this.aff2_speaks = aff2_speaks;
	}

	public Double getNeg1_speaks() {
		return neg1_speaks;
	}

	public void setNeg1_speaks(Double neg1_speaks) {
		this.neg1_speaks = neg1_speaks;
	}

	public Double getNeg2_speaks() {
		return neg2_speaks;
	}

	public void setNeg2_speaks(Double neg2_speaks) {
		this.neg2_speaks = neg2_speaks;
	}

	public Integer getAff1_place() {
		return aff1_place;
	}

	public void setAff1_place(Integer aff1_place) {
		this.aff1_place = aff1_place;
	}

	public Integer getAff2_place() {
		return aff2_place;
	}

	public void setAff2_place(Integer aff2_place) {
		this.aff2_place = aff2_place;
	}

	public Integer getNeg1_place() {
		return neg1_place;
	}

	public void setNeg1_place(Integer neg1_place) {
		this.neg1_place = neg1_place;
	}

	public Integer getNeg2_place() {
		return neg2_place;
	}

	public void setNeg2_place(Integer neg2_place) {
		this.neg2_place = neg2_place;
	}

	public String getDecision() {
		return decision;
	}

	public void setDecision(String decision) {
		this.decision = decision;
	}

	public Round getRound() {
		return round;
	}

	public void setRound(Round round) {
		this.round = round;
	}

	public void replaceNull(Ballot ballot) {
		if(round == null)
			round = ballot.getRound();
		if(aff1_speaks == null)
			aff1_speaks = ballot.getAff1_speaks();
		if(aff2_speaks == null)
			aff2_speaks = ballot.getAff2_speaks();
		if(neg1_speaks == null)
			neg1_speaks = ballot.getNeg1_speaks();
		if(neg2_speaks == null)
			neg2_speaks = ballot.getNeg2_speaks();
		if(aff1_place == null)
			aff1_place = ballot.getAff1_place();
		if(aff2_place == null)
			aff2_place = ballot.getAff2_place();
		if(neg1_place == null)
			neg1_place = ballot.getNeg1_place();
		if(neg2_place == null)
			neg2_place = ballot.getNeg2_place();
		if(decision == null)
			decision = ballot.getDecision();
	}
}
