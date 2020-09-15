package net.debaterank.server.models;

import javax.persistence.*;

@MappedSuperclass
public class DuoRound {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    @ManyToOne
    @JoinColumn(nullable = false)
    private Tournament tournament;
    @ManyToOne
    @JoinColumn
    private Team a;
    @ManyToOne
    @JoinColumn
    private Team n;
    private boolean bye;
    private String round;
    private String absUrl;
    private double aBefore, aAfter, nBefore, nAfter;

    public DuoRound() {}

    public DuoRound(Tournament t) {
        tournament = t;
    }

    public DuoRound(Tournament t, Team a, Team n) {
        tournament = t;
        this.a = a;
        this.n = n;
    }

    public double getaBefore() {
        return aBefore;
    }

    public void setaBefore(double aBefore) {
        this.aBefore = aBefore;
    }

    public double getaAfter() {
        return aAfter;
    }

    public void setaAfter(double aAfter) {
        this.aAfter = aAfter;
    }

    public double getnBefore() {
        return nBefore;
    }

    public void setnBefore(double nBefore) {
        this.nBefore = nBefore;
    }

    public double getnAfter() {
        return nAfter;
    }

    public void setnAfter(double nAfter) {
        this.nAfter = nAfter;
    }

    public boolean isBye() {
        return bye;
    }

    public void setBye(boolean bye) {
        this.bye = bye;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public String getAbsUrl() {
        return absUrl;
    }

    public void setAbsUrl(String absUrl) {
        this.absUrl = absUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public void setTournament(Tournament tournament) {
        this.tournament = tournament;
    }

    public Team getA() {
        return a;
    }

    public void setA(Team a) {
        this.a = a;
    }

    public Team getN() {
        return n;
    }

    public void setN(Team n) {
        this.n = n;
    }

}
