package net.debaterank.server.entities;

import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.NameTokenizer;
import org.hibernate.Session;

import javax.persistence.*;
import java.io.Serializable;

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
	@OneToOne
	private School school;
	@OneToOne
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
		this(name, new School(school.trim().equals(" ") ? null : school.trim()));
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

	public static Debater getDebaterFromLastName(String last, School school) {
		Session session = HibernateUtil.getSession();
		return (Debater)session.createQuery("from Debater where last = :n and school = :s ")
				.setParameter("n", last)
				.setParameter("s", school)
				.getSingleResult();
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
}
