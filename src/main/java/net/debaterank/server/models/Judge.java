package net.debaterank.server.models;

import net.debaterank.server.util.HibernateUtil;
import net.debaterank.server.util.NameTokenizer;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.persistence.*;

import java.util.List;

import static net.debaterank.server.util.DRHelper.isSameName;
import static net.debaterank.server.util.DRHelper.replaceNull;

@Entity
@Table
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

	public static Judge getJudge(Judge judge) {
		if(judge == null) return null;
		Session session = HibernateUtil.getSession();
		try {
			List<Judge> results = (List<Judge>) session.createQuery("from Judge where first = :f and last = :l")
					.setParameter("f", judge.getFirst())
					.setParameter("l", judge.getLast())
					.getResultList();
			for (Judge j : results) {
				if (judge.equals(j)) {
					replaceNull(j, judge);
					return j;
				}
			}
			return null;
		} finally {
			session.close();
		}
	}

	public static Judge getJudgeOrInsert(Judge judge) {
		if(judge == null) return null;
		Judge j = getJudge(judge);
		if(j != null) return j;
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
