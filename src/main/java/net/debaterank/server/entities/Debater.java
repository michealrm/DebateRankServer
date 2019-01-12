package net.debaterank.server.entities;

import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.NameTokenizer;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import static net.debaterank.server.util.DRHelper.isSameName;

@Entity
@Table
public class Debater implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	private String first;
	private String middle;
	private String last;
	private String suffix;
	@ManyToOne
	@JoinColumn
	private School school;
	@OneToOne
	@JoinColumn
	private Debater pointsTo;

	public Debater() {}

	public Debater(Long id, String first, String middle, String last, String suffix, School school) {
		this.id = id;
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.suffix = suffix;
		this.school = school;
	}

	public Debater(String first, String middle, String last, String suffix, School school) {
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.suffix = suffix;
		this.school = school;
	}

	public Debater(String name, String school) {
		this(name, new School(school == null || school.trim().equals(" ") ? null : school.trim()));
	}

	public Debater(String name, School school) {
		NameTokenizer nt = new NameTokenizer(name);
		this.first = nt.getFirst();
		this.middle = nt.getMiddle();
		this.last = nt.getLast();
		this.suffix = nt.getSuffix();
		this.school = school;
	}

	public boolean equals(Debater debater) {
		if(debater == null)
			return false;
		String first = debater.getFirst();
		String last = debater.getLast();
		School school = debater.getSchool();
		if(this.school != null && school != null && !this.school.equals(school)) {
			return false;
		}
		return (isSameName(this.first, first) && isSameName(this.last, last));
	}

	public void replaceNull(Debater d) {
		if(id == null) id = d.getId();
		if(d.getId() == null) d.setId(id);
		if(first == null) first = d.getFirst();
		if(d.getFirst() == null) d.setFirst(first);
		if(middle == null) middle = d.getMiddle();
		if(d.getMiddle() == null) d.setMiddle(middle);
		if(last == null) last = d.getLast();
		if(d.getLast() == null) d.setLast(last);
		if(suffix == null) suffix = d.getSuffix();
		if(d.getSuffix() == null) d.setSuffix(suffix);
		if(school == null) school = d.getSchool();
		if(d.getSchool() == null) d.setSchool(school);
	}

	public static Debater getDebaterFromLastName(String last, School school) {
		Session session = HibernateUtil.getSession();
		return (Debater)session.createQuery("from Debater where last = :n and school = :s ")
				.setParameter("n", last)
				.setParameter("s", school)
				.getSingleResult();
	}

	public static Debater getDebater(Debater debater) {
		if(debater == null) return null;
		Session session = HibernateUtil.getSession();
		try {
			List<Debater> results = (List<Debater>) session.createQuery("from Debater where first = :f and last = :l")
					.setParameter("f", debater.getFirst())
					.setParameter("l", debater.getLast())
					.getResultList();
			for (Debater d : results) {
				if (debater.equals(d)) {
					d.replaceNull(debater);
					return d;
				}
			}
			return null;
		} finally {
			session.close();
		}
	}

	public static Debater getDebaterOrInsert(Debater debater) {
		if(debater == null) return null;
		Debater d = getDebater(debater);
		if(d != null) return d;
		Session session = HibernateUtil.getSession();
		try {
			Transaction t = session.beginTransaction();
			debater.setSchool(School.getSchoolOrInsert(debater.getSchool())); // TODO: maybe replace the ID then replace nulls?
			session.save(debater);
			t.commit();
			return debater;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			session.close();
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFirst() {
		return first;
	}

	public void setFirst(String first) {
		this.first = first;
	}

	public String getMiddle() {
		return middle;
	}

	public void setMiddle(String middle) {
		this.middle = middle;
	}

	public String getLast() {
		return last;
	}

	public void setLast(String last) {
		this.last = last;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	public School getSchool() {
		return school;
	}

	public void setSchool(School school) {
		this.school = school;
	}

	public Debater getPointsTo() {
		return pointsTo;
	}

	public void setPointsTo(Debater pointsTo) {
		this.pointsTo = pointsTo;
	}

	public String toString() {
		return first + " " + middle + " " + last + " " + suffix + ", " + (school == null ? null : school.toString());
	}

}
