package net.debaterank.server.models;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.*;

import java.util.List;

@Entity("rounds")
public class Round {

    public enum Type {
        LD,
        PF,
        CX
    }

    @Id
    private ObjectId id = new ObjectId();
    @Reference
    private Tournament tournament;
    @Property
    private Type type;
    @Reference("s_aff")
    private Debater singleAff;
    @Reference("s_neg")
    private Debater singleNeg;
    @Reference("t_aff")
    private Team teamAff;
    @Reference("t_neg")
    private Team teamNeg;
    @Property
    private boolean bye;
    @Property
    private String absUrl;
    @Property
    private String round;
    @Property
    private boolean noSide;

    public boolean isNoSide() {
        return noSide;
    }

    public void setNoSide(boolean noSide) {
        this.noSide = noSide;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
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

    public Tournament getTournament() {
        return tournament;
    }

    public void setTournament(Tournament tournament) {
        this.tournament = tournament;
    }

    public Round(Tournament tournament) {
        this.tournament = tournament;
    }

    public Round(Tournament tournament, Team aff, Team neg, Type type, boolean bye, String absUrl, String round, boolean noSide) {
        this.tournament = tournament;
        this.type = type;
        teamAff = aff;
        teamNeg = neg;
        this.bye = bye;
        this.absUrl = absUrl;
        this.round = round;
        this.noSide = noSide;
    }

    public Round(Tournament tournament, Debater aff, Debater neg, boolean bye, String absUrl, String round, boolean noSide) {
        this.tournament = tournament;
        type = Type.LD;
        singleAff = aff;
        singleNeg = neg;
        this.bye = bye;
        this.absUrl = absUrl;
        this.round = round;
        this.noSide = noSide;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Debater getSingleAff() {
        return singleAff;
    }

    public void setSingleAff(Debater singleAff) {
        this.singleAff = singleAff;
    }

    public Debater getSingleNeg() {
        return singleNeg;
    }

    public void setSingleNeg(Debater singleNeg) {
        this.singleNeg = singleNeg;
    }

    public Team getTeamAff() {
        return teamAff;
    }

    public void setTeamAff(Team teamAff) {
        this.teamAff = teamAff;
    }

    public Team getTeamNeg() {
        return teamNeg;
    }

    public void setTeamNeg(Team teamNeg) {
        this.teamNeg = teamNeg;
    }
}
