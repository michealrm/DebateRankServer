package net.debaterank.server.entities;

import javax.persistence.*;
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
	private boolean scraped;

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

	public boolean isScraped() {
		return scraped;
	}

	public void setScraped(boolean scraped) {
		this.scraped = scraped;
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
