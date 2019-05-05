package net.debaterank.server.entities;

import javax.persistence.*;

@MappedSuperclass
public class Ballot<T extends Round> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn
    private T round;
    @ManyToOne
    @JoinColumn
    private Judge judge;
    private String decision;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public T getRound() {
        return round;
    }

    public void setRound(T round) {
        this.round = round;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Judge getJudge() {
        return judge;
    }

    public void setJudge(Judge judge) {
        this.judge = judge;
    }

    public Ballot() {}

    public Ballot(T round) {
        this.round = round;
    }

}
