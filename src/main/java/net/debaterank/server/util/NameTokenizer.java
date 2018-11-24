package net.debaterank.server.util;

public class NameTokenizer {

	private String name, first, middle, last, suffix;

	public NameTokenizer(String name) {
		name = name.trim().replaceAll(" \\(.+?\\)", "");
		String[] blocks = name.split(" ");
		if(blocks.length == 0)
			first = "";
		else
			first = blocks[0];
		if(blocks.length == 2)
			last = blocks[1];
		else if(blocks.length == 3) {
			if(blocks[2].endsWith(".") || blocks[2].length() == 2 || blocks[2].matches("III|IV|V|VI|VII|VIII|IX|X|Junior")) {
				last = blocks[1];
				suffix = blocks[2];
			}
			else {
				middle = blocks[1];
				last = blocks[2];
			}
		}
		else if(blocks.length >= 4) {
			middle = blocks[1];
			last = blocks[2];
			suffix = blocks[3];
		}
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String getFirst() {
		return first;
	}

	public String getMiddle() {
		return middle;
	}

	public String getLast() {
		return last;
	}

	public String getSuffix() {
		return suffix;
	}
}
