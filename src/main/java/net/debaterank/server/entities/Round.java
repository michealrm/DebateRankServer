package net.debaterank.server.entities;

import javax.persistence.*;

@MappedSuperclass
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    @ManyToOne
    @JoinColumn(nullable = false)
    private Tournament tournament;
    private boolean bye;
    private String round;
    private String absUrl;

    public Round(Tournament t) {
        tournament = t;
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
}
