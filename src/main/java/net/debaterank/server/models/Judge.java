package net.debaterank.server.models;

import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.NameTokenizer;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import java.util.List;

import static net.debaterank.server.util.DRHelper.isSameName;
import static net.debaterank.server.util.DRHelper.replaceNull;

@Entity
@Table
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
/**
 * Note: Cache is read-only, so this application shouldn't make any updates to the object. This is expected because
 * this server should function as an unmanaged, single instance scraper only. Note: if this application is changed
 * to be distributed then cache usage should either be shared across the nodes OR turned off entirely
 * Updates to this object should be make in a separate application (with moderation). The cache should be reset
 * or this server can be restarted after changes are pushed
 */
public class Judge {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	private String first, middle, last, suffix;
	@ManyToOne
	@JoinColumn
	private School school;

	public boolean equals(Judge judge) {
		if(judge == null)
			return false;
		String first = judge.getFirst();
		String last = judge.getLast();
		return (isSameName(this.first, first) && isSameName(this.last, last));
	}

	public School getSchool() {
		return school;
	}

	public void setSchool(School school) {
		this.school = school;
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

	public Judge(String name) {
		NameTokenizer nt = new NameTokenizer(name);
		first = nt.getFirst();
		middle = nt.getMiddle();
		last = nt.getLast();
		suffix = nt.getSuffix();
	}

	public Judge(String name, School school) {
		this(name);
		this.school = school;
	}

	public Judge() {}

	public Judge(String first, String middle, String last, String suffix) {
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.suffix = suffix;
	}

	public static Judge getJudgeFromLastName(String last, School school) {
		Session session = HibernateUtil.getSession();
		return (Judge)session.createQuery("from Judge where last = :n and school = :s ")
				.setParameter("n", last)
				.setParameter("s", school)
				.getSingleResult();
	}

	public static List<Judge> getJudges() {
		Session session = HibernateUtil.getSession();
		try {
			CriteriaQuery<Judge> query = session.getCriteriaBuilder().createQuery(Judge.class);
			query.select(
					query.from(Judge.class)
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

	public static Judge getJudge(Judge judge) {
		if(judge == null) return null;
		Session session = HibernateUtil.getSession();
		try {
			CriteriaBuilder builder = session.getCriteriaBuilder();

			CriteriaQuery<Judge> query = builder.createQuery(Judge.class);
			Root<Judge> root = query.from(Judge.class);
			query.where(
					builder.or(
							builder.equal(
									builder.lower(root.get("first")),
									toLowerCase(judge.getFirst())
							),
							builder.equal(
									builder.lower(root.get("last")),
									toLowerCase(judge.getLast())
							)
					)
			);
			List<Judge> results = session.createQuery(query)
					.setCacheable(true)
					.list();
			for (Judge d : results) {
				if (judge.equals(d)) {
					replaceNull(d, judge);
					return d;
				}
			}
			return null;
		} finally {
			session.close();
		}
	}

	public static Judge getJudgeOrInsert(Judge judge) {
		if(judge == null) return null;
		Judge d = getJudge(judge);
		if(d != null) return d;
		return insertJudge(judge);
	}

	public static Judge insertJudge(Judge judge) {
		if(judge == null) return null;
		Session session = HibernateUtil.getSession();
		try {
			Transaction t = session.beginTransaction();
			judge.setSchool(School.getSchoolOrInsert(judge.getSchool())); // TODO: maybe replace the ID then replace nulls?
			session.save(judge);
			t.commit();
			return judge;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			session.close();
		}
	}
	
}
