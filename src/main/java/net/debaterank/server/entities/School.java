package net.debaterank.server.entities;

import net.debaterank.server.Server;

import javax.persistence.*;
import java.io.Serializable;

import static net.debaterank.server.util.DRHelper.isSameName;

@Entity
@Table
public class School implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;
	private String name;
	private String address;
	private String state;
	private String nsda_link;

	public boolean equals(School school) {
		return isSameName(state, school.getState()) &&
				(isSameName(address, school.getAddress()) ||
						isSameName(nsda_link, school.getNsda_link()) ||
						isSameName(name, school.getName()));
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

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getNsda_link() {
		return nsda_link;
	}

	public void setNsda_link(String nsda_link) {
		this.nsda_link = nsda_link;
	}

	public School() {
	}

	public School(String name, String address, String state, String nsda_link) {
		this.name = name;
		this.address = address;
		this.state = state;
		this.nsda_link = nsda_link;
	}

	public School(String name) {
		this.name = name;
	}
}
