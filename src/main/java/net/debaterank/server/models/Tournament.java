package net.debaterank.server.models;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table
public class Tournament implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
	private String circuit;
	private String name;
	private String link;
	private String state;
	@Temporal(TemporalType.DATE)
	private Date date;
	private boolean ldScraped;
	private boolean pfScraped;
	private boolean cxScraped;

	public boolean isScraped(String event) {
		if(event.equals("LD")) return ldScraped;
		else if(event.equals("PF")) return pfScraped;
		else if (event.equals("CX")) return cxScraped;
		else return false;
	}

	public void setScraped(String event, boolean scraped) {
		if(event.equals("LD")) ldScraped = scraped;
		else if(event.equals("PF")) pfScraped = scraped;
		else if (event.equals("CX")) cxScraped = scraped;
		else return;
	}

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

	public void setCircuit(String circuit) {this.circuit = circuit;}

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
