package net.debaterank.server.models;

import net.debaterank.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table
public class Team {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	@ManyToOne
	@JoinColumn
	private Debater one;
	@ManyToOne
	@JoinColumn
	private Debater two;

	public Team() {}

	public Team(Debater one, Debater two) {
		this.one = one;
		this.two = two;
	}

	public boolean equalsByLastName(Team team) {
		return (one.getLast().equals(team.getOne().getLast()) && two.getLast().equals(team.getTwo().getLast())) || (two.getLast().equals(team.getOne().getLast()) && one.getLast().equals(team.getTwo().getLast()));
	}

	public boolean equals(Team team) {
		return Objects.equals(one, team.getOne()) && Objects.equals(two, team.getTwo());
	}

	public Debater getOne() {
		return one;
	}

	public void setOne(Debater one) {
		this.one = one;
	}

	public Debater getTwo() {
		return two;
	}

	public void setTwo(Debater two) {
		this.two = two;
	}

	public static Team getTeam(Team team) {
		if(team == null) return null;
		Debater d1 = Debater.getDebater(team.getOne());
		Debater d2 = Debater.getDebater(team.getTwo());
		Long id1 = d1 == null ? null : d1.getId();
		Long id2 = d2 == null ? null : d2.getId();
		Session session = HibernateUtil.getSession();
		try {
			Team result = (Team) session.createQuery("from Team where one = :o and two = :t")
					.setParameter("o", id1)
					.setParameter("t", id2)
					.uniqueResult();
			return result;
		} finally {
			session.close();
		}
	}

	public static Team getTeamOrInsert(Team team) {
		if(team == null) return null;
		Debater d1 = Debater.getDebaterOrInsert(team.getOne());
		Debater d2 = Debater.getDebaterOrInsert(team.getTwo());
		Session session = HibernateUtil.getSession();
		try {
			Team result = (Team) session.createQuery("select t from Team t where one = :o and two = :t")
					.setParameter("o", d1)
					.setParameter("t", d2)
					.uniqueResult();
			if (result != null) return result;
			Transaction transaction = session.beginTransaction();
			session.save(team);
			transaction.commit();
			return team;
		} finally {
			session.close();
		}
	}

}
