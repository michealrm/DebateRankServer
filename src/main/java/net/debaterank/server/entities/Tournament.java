package net.debaterank.server.entities;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table
public class Tournament {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
	private String name;
	@Column(unique = true)
	private String link;
	private String state;
	@Temporal(TemporalType.DATE)
	private Date date;
	private boolean ldScraped;
	private boolean pfScraped;
	private boolean cxScraped;

	public boolean isLdScraped() {
		return ldScraped;
	}

	public void setLdScraped(boolean ldScraped) {
		this.ldScraped = ldScraped;
	}

	public boolean isPfScraped() {
		return pfScraped;
	}

	public void setPfScraped(boolean pfScraped) {
		this.pfScraped = pfScraped;
	}

	public boolean isCxScraped() {
		return cxScraped;
	}

	public void setCxScraped(boolean cxScraped) {
		this.cxScraped = cxScraped;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Tournament() {
	}

	public Tournament(long id, String name, String link, String state, Date date) {
		this.id = id;
		this.name = name;
		this.link = link;
		this.state = state;
		this.date = date;
	}

	public Tournament(String name, String link, String state, Date date) {
		this.name = name;
		this.link = link;
		this.state = state;
		this.date = date;
	}

}
