package net.debaterank.server.entities;

import net.debaterank.server.util.NameTokenizer;

import javax.persistence.*;

import static net.debaterank.server.util.DRHelper.isSameName;

@Entity
@Table
public class Judge {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	private String first, middle, last, suffix;

	public boolean equals(Judge judge) {
		if(judge == null)
			return false;
		String first = judge.getFirst();
		String last = judge.getLast();
		return (isSameName(this.first, first) && isSameName(this.last, last));
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

	public Judge() {}

	public Judge(String first, String middle, String last, String suffix) {
		this.first = first;
		this.middle = middle;
		this.last = last;
		this.suffix = suffix;
	}
}
