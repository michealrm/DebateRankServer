package net.debaterank.server.models;

import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.NameTokenizer;
import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Cache;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.criterion.Restrictions;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.Serializable;
import java.util.List;

import static net.debaterank.server.util.DRHelper.*;

import static net.debaterank.server.util.DRHelper.isSameName;

@Entity
@Table
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
/**
 * Note: Cache is read-only, so this application shouldn't make any updates to the object. This is expected because
 * this server should function as an unmanaged, single instance scraper only. Note: if this application is changed
 * to be distributed then cache usage should either be shared across the nodes OR turned off entirely
 * Updates to this object should be make in a separate application (with moderation). The cache should be reset
 * or this server can be restarted after changes are pushed
 */
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

	public static Debater getDebaterFromLastName(String last, School school) {
		Session session = HibernateUtil.getSession();
		return (Debater)session.createQuery("from Debater where last = :n and school = :s ")
				.setParameter("n", last)
				.setParameter("s", school)
				.getSingleResult();
	}

	public static List<Debater> getDebaters() {
		Session session = HibernateUtil.getSession();
		try {
			CriteriaQuery<Debater> query = session.getCriteriaBuilder().createQuery(Debater.class);
			query.select(
					query.from(Debater.class)
			);
			return session.createQuery(query)
					.setCacheable(true)
					.list();
		} finally {
			session.close();
		}
	}

	private static String toLowerCase(String s) {
		if(s == null)
			return null;
		else
			return s.toLowerCase();
	}

	public static Debater getDebater(Debater debater) {
		if(debater == null) return null;
		Session session = HibernateUtil.getSession();
		try {
			CriteriaBuilder builder = session.getCriteriaBuilder();

			CriteriaQuery<Debater> query = builder.createQuery(Debater.class);
			Root<Debater> root = query.from(Debater.class);
			query.where(
					builder.or(
							builder.equal(
								builder.lower(root.get("first")),
								toLowerCase(debater.getFirst())
							),
							builder.equal(
								builder.lower(root.get("last")),
								toLowerCase(debater.getLast())
							)
					)
			);
			List<Debater> results = session.createQuery(query)
					.setCacheable(true)
					.list();
			for (Debater d : results) {
				if (debater.equals(d)) {
					replaceNull(d, debater);
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
		return insertDebater(debater);
	}

	public static Debater insertDebater(Debater debater) {
		if(debater == null) return null;
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
