package io.micheal.debatescout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class Main {

	public static Log log;
	public static boolean active = true;
	
	static {
		log = LogFactory.getLog(Main.class);
		log.debug("Instantiated logger");
	}
	
	public static void main(String[] args) {
		
		while(active) {
			try {
				Thread.sleep(1); // Change to next update time (defined in config)
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			// JOT Results
			
			// http://www.joyoftournaments.com/results.asp
			
			try {
				String html = getHTML("http://www.joyoftournaments.com/results.asp");
				String seasonsHTML = html.substring(html.indexOf("select name=\"season\""), html.indexOf("</select>", html.indexOf("select name=\"season\"")));
			    ArrayList<String> seasons = new ArrayList<String>();
				Pattern r = Pattern.compile("option value=(\\d{4})");
			    Matcher m = r.matcher(seasonsHTML);
			    while(m.find())
			    	seasons.add(m.group(1));
			    if(seasons.size() == 0)
			    	throw new Exception("Couldn't parse seasons");
			    
			    ArrayList<Tournament> tournaments = new ArrayList<Tournament>();
			    
			    for(String season : seasons) {
			    	html = post("http://www.joyoftournaments.com/results.asp", new BasicNameValuePair("state", ""), new BasicNameValuePair("month", "0"), new BasicNameValuePair("season", season));
			    	html = html.replaceAll("\t|\n|\r", "");
			    	r = Pattern.compile("<tr><td align='center'>(.+?)<\\/td><td><a href='(.+?)'>(.+?)<\\/a><\\/td><\\/td><td align='right'>(.+?)<\\/td><\\/tr>");
				    m = r.matcher(html);
				    while(m.find()) {
				    	String state = m.group(1);
				    	String link = m.group(2);
				    	String name = m.group(3);
				    	String date = m.group(4);

				    	tournaments.add(new Tournament(name, link, state, date));
				    	
				    }
			    }
			    
			    log.info(tournaments.size() + " tournaments scraped from JOT");
			    
			    for(Tournament t : tournaments) {
			    	try {
				    	html = getHTML(t.getLink());
				    	/*
				    	 * <a href='(.+?)' title=\".*?(LD|Lincoln|L-D).*?\">Prelims<\\/a>
	<a href='(.+?)' title=\".*?(LD|Lincoln|L-D).*?\">Packet<\\/a>
	
	
	<a href='(.+?)' title=\".*?(CX|Cross|C-X|Policy).*?\">Prelims<\\/a>
	<a href='(.+?)' title=\".*?(CX|Cross|C-X|Policy).*?\">Packet<\\/a>
	
	<a href='(.+?)' title=\".*?(PF|Public|P-F).*?\">Prelims<\\/a>
	<a href='(.+?)' title=\".*?(PF|Public|P-F).*?\">Packet<\\/a>
				    	 */
				    	r = Pattern.compile("<a href='(.+?)' title=\".*?(LD|Lincoln|L-D).*?\">Prelims<\\/a>");
				    	m = r.matcher(html);
				    	if(m.find()) {
				    		html = getHTML(t.getLink() + "/" + m.group(1));
				    		html = html.replaceAll("\\t|\\n|\\r", "");
				    		r = Pattern.compile("<td>(.+?)<\\/td>|<td width='50%' align=left>(.+?)<\\/td>|<td width='50%' align=right>(.+?)<\\/td>|<td colspan=2 class='rec'>(.+?)<\\/td>|<td colspan=2 align='right'>(.+?)<\\/td>");
				    		m = r.matcher(html);
				    		ArrayList<String> lines = new ArrayList<String>();
				    		while(m.find())
				    			for(int i = 1; i<=5;i++)
				    				if(m.group(i) != null)
				    					if(m.group(i).equals("&nbsp;"))
				    							lines.add("");
				    					else
				    						lines.add(m.group(i));
				    		for(int i = 0;i<lines.size();i++) {
				    			String school = lines.get(i);
				    			String code = lines.get(++i);
				    			String name = lines.get(++i);
				    			if(name.contains("(drop)") && !lines.get(i+1).equals(""))
				    				continue;
				    			ArrayList<Round> rounds = new ArrayList<Round>();
				    			while(!lines.get(++i).equals("")) {
				    				boolean win;
				    				if(lines.get(i).equals("B")) {
				    					if(lines.get(i-1).contains("\\*"))
				    						win = true;
				    					else
				    						win = false;
				    					boolean breakOut = (lines.get(i+2).equals(""));
				    					i++;
				    					while(lines.get(i).equals("") || lines.get(i).equals("B"))
					    					i++;
				    					i-=1;
				    					if(breakOut)
				    						break;
				    					else {
				    						continue;
				    					}
				    					
				    				}
				    				double speaks = Double.parseDouble(lines.get(i).replaceAll("\\*", ""));
				    				win = lines.get(++i).equals("W");
				    				if(lines.get(i).equals("B")) {
				    					if(lines.get(i-1).contains("\\*"))
				    						win = true;
				    					else
				    						win = false;
				    					boolean breakOut = (lines.get(i+2).equals(""));
				    					i++;
				    					while(lines.get(i).equals("") || lines.get(i).equals("B"))
					    					i++;
				    					i-=1;
				    					if(breakOut)
				    						break;
				    					else {
				    						continue;
				    					}
				    					
				    				}
				    				String against = lines.get(++i);
				    				rounds.add(new Round(against, win, speaks));
				    			}
				    			if(i+1 < lines.size() && lines.get(i+1).equals("F")) {
				    				rounds.add(new Round(null, false, -1));
				    				i++;
				    				while(lines.get(i).equals("") || lines.get(i).equals("F") || lines.get(i).equals("B") || lines.get(i).equals("-1"))
				    					i++;
				    				i-=1;
				    			}
				    			if(name.contains("Micheal Myers")) {
				    				System.out.println(name + "\n" + t.getName() + "\n");
				    				for(Round round : rounds)
				    					System.out.println("\t" + (round.getWin() ? "Won" : "Lost") + " against " + round.getOpponent() + " with " + round.getSpeaks() + " speaks.");
				    			}
				    			
				    		}
				    	}
			    	}
			    	catch(Exception e) {
			    		e.printStackTrace();
			    		log.error(e);
			    		log.fatal("Error while parsing tournament " + t.getName() + ". Skipping.");
			    	}
			    }
			    
			    
			} catch (Exception e) {
				log.fatal("Could not parse joyoftournament results");
				e.printStackTrace();
			}
			
			log.info("Updated database");
			System.exit(0);
		}
		
	}
	
	private static String getHTML(String url) { // TODO: Thread pool
		URLConnection connection = null;
		try {
		  connection =  new URL(url).openConnection();
		  Scanner scanner = new Scanner(connection.getInputStream());
		  scanner.useDelimiter("\\Z");
		  String content = scanner.next();
		  scanner.close();
		  return content;
		} catch ( Exception ex ) {
		    ex.printStackTrace();
		}
		
		return null;
	}
	
	private static String post(String uri, BasicNameValuePair... pairs ) throws ClientProtocolException, IOException {
		HttpClient httpclient = HttpClients.createDefault();
		HttpPost httppost = new HttpPost(uri);

		List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		for(BasicNameValuePair pair : pairs)
			params.add(pair);
		httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

		HttpResponse response = httpclient.execute(httppost);
		HttpEntity entity = response.getEntity();

		if (entity != null) {
		    InputStream instream = entity.getContent();
		    try {
		    	Scanner scanner = new Scanner(instream);
		    	scanner.useDelimiter("\\Z");
		    	String content = scanner.next();
		    	scanner.close();
		    	return content;
		    } finally {
		        instream.close();
		    }
		}
		
		return null;
	}
	
}
