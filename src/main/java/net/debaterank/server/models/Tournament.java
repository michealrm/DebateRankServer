package net.debaterank.server.models;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Property;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity("tournaments")
public class Tournament {

	@Id
	private ObjectId id;
	private String name;
	private String link;
	private String state;
	private Date date;
    private HashMap<String, Boolean> scraped;

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
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

    public HashMap<String, Boolean> getScraped() {
        return scraped;
    }

    public void setScraped(HashMap<String, Boolean> scraped) {
        this.scraped = scraped;
    }

    public boolean isScraped(String event) {
	    if(scraped == null)
	        return false;
	    return scraped.get(event);
    }

    public void putScraped(String event, boolean val) {
	    scraped.put(event, val);
    }

    public void replaceNull(Tournament tournament) {
		if(id == null)
			id = tournament.getId();
		if(name == null)
			name = tournament.getName();
		if(link == null)
			link = tournament.getLink();
		if(state == null)
			state = tournament.getState();
		if(date == null)
			date = tournament.getDate();
		if(scraped == null)
            scraped = tournament.getScraped();
	}

	public Tournament(ObjectId id, String name, String link, String state, Date date) {
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

	public Tournament() {}

}
