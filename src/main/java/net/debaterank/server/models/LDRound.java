package net.debaterank.server.models;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;

import java.util.List;

@Entity("ld_rounds")
public class LDRound {

	@Id
	private ObjectId id = new ObjectId();
	@Reference
	private Debater aff;
	@Reference
	private Debater neg;
	private boolean bye;
	private String absUrl, round;
	@Embedded
	private List<Ballot> ballot;
	private boolean noSide;

	public boolean isNoSide() {
		return noSide;
	}

	public void setNoSide(boolean noSide) {
		this.noSide = noSide;
	}

	public LDRound() {}

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

	public Debater getAff() {
		return aff;
	}

	public void setAff(Debater aff) {
		this.aff = aff;
	}

	public Debater getNeg() {
		return neg;
	}

	public void setNeg(Debater neg) {
		this.neg = neg;
	}

	public boolean isBye() {
		return bye;
	}

	public void setBye(boolean bye) {
		this.bye = bye;
	}

	public String getAbsUrl() {
		return absUrl;
	}

	public void setAbsUrl(String absUrl) {
		this.absUrl = absUrl;
	}

	public String getRound() {
		return round;
	}

	public void setRound(String round) {
		this.round = round;
	}

	public List<Ballot> getBallot() {
		return ballot;
	}

	public void setBallot(List<Ballot> ballot) {
		this.ballot = ballot;
	}

	public LDRound(Debater aff, Debater neg, boolean bye, String absUrl, String round, boolean noSide) {

		this.aff = aff;
		this.neg = neg;
		this.bye = bye;
		this.absUrl = absUrl;
		this.round = round;
		this.noSide = noSide;
	}

	public LDRound(Debater aff, Debater neg, boolean bye, String absUrl, String round, List<Ballot> ballot, boolean noSide) {

		this.aff = aff;
		this.neg = neg;
		this.bye = bye;
		this.absUrl = absUrl;
		this.round = round;
		this.ballot = ballot;
		this.noSide = noSide;
	}
}
