package io.micheal.debaterank.modules.tabroom;

import io.micheal.debaterank.Debater;
import io.micheal.debaterank.School;
import io.micheal.debaterank.Tournament;
import io.micheal.debaterank.modules.Module;
import io.micheal.debaterank.modules.WorkerPool;
import io.micheal.debaterank.util.DebateHelper;
import io.micheal.debaterank.util.SQLHelper;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;

import javax.xml.namespace.QName;
import javax.xml.parsers.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;

import static io.micheal.debaterank.util.DebateHelper.JOT;
import static io.micheal.debaterank.util.DebateHelper.tournamentExists;

public class LD extends Module {

	private ArrayList<Tournament> tournaments;
	private WorkerPool manager;
	private final boolean overwrite;
	private int count = 0;

	public LD(SQLHelper sql, Logger log, ArrayList<Tournament> tournaments, WorkerPool manager) {
		super(sql, log);
		this.tournaments = tournaments;
		this.manager = manager;

		Configuration config;
		boolean temp;
		try {
			config = new Configurations().properties(new File("config.properties"));
			temp = config.getBoolean("overwrite");
		} catch (ConfigurationException e) {
			log.error(e);
			log.fatal("Could not read config for overwrite boolean. Default false");
			temp = false;
		}
		overwrite = temp;
	}

	public void run() {
		for(Tournament t : tournaments) {
			long one = System.currentTimeMillis();
			manager.newModule(new Runnable() {
				public void run() {
					try {

						String tournIDStr = "";
						int index = t.getLink().indexOf("tourn_id=") + 8;
						while(index < t.getLink().length()) {
							index++;
							try {
								Integer.parseInt(Character.toString(t.getLink().toCharArray()[index]));
							}
							catch(Exception e) {
								break;
							}
							tournIDStr += Character.toString(t.getLink().toCharArray()[index]);
						}

						int tourn_id = Integer.parseInt(tournIDStr);



						String[] split = t.getDate().split("\\/");
						URL url = new URL("https://www.tabroom.com/api/current_tournaments.mhtml?timestring=" + split[2] + "-" + split[0] + "-" + split[1] + "T12:00:00");

						SAXParserFactory factory = SAXParserFactory.newInstance();
						SAXParser saxParser = factory.newSAXParser();

						DefaultHandler handler = new DefaultHandler() {

							private boolean bevent, beventname, bid, btourn;
							private String eventname;
							private int event_id, tid;

							public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
								if(qName.equalsIgnoreCase("EVENT")) {
									bevent = true;
									eventname = null;
									event_id = 0;
									tid = 0;
								}
								if(qName.equalsIgnoreCase("EVENTNAME") && bevent)
									beventname = true;
								if(qName.equalsIgnoreCase("ID") && bevent)
									bid = true;
								if(qName.equalsIgnoreCase("TOURN") && bevent)
									btourn = true;
							}

							public void characters(char ch[], int start, int length) throws SAXException {
								if(beventname) {
									beventname = false;
									eventname = new String(ch, start, length);
								}
								if(bid) {
									bid = false;
									event_id = Integer.parseInt(new String(ch, start, length));
								}
								if(btourn) {
									btourn = false;
									tid = Integer.parseInt(new String(ch, start, length));
								}
								if(eventname != null && event_id != 0 && tid != 0) {
									bevent = false;
									if (eventname.matches("^.*(LD|Lincoln|L-D).*$") && tourn_id == tid) {
										try {
											int entries;
											if ((entries = getTournamentRoundEntries(tourn_id, event_id)) == 0)
												return;

											// If we have the same amount of entries, then do not check
											//if (tournamentExists(t.getLink() + "|" + event_id, getTournamentRoundEntries(tourn_id, event_id), sql, "ld_rounds"))
											//	log.log(JOT, t.getName() + " prelims is up to date.");

											// Overwrite
											//if (overwrite)
											//	sql.executePreparedStatementArgs("DELETE FROM ld_rounds WHERE absUrl=?", t.getLink() + "|" + event_id);

											System.out.println(entries + " " + (System.currentTimeMillis() - one));
										} catch (XMLStreamException xmlse) {
										} catch (IOException ioe) {}
										catch(ParserConfigurationException pce) {}
									}
								}
							}
						};

						saxParser.parse(url.openStream(), handler);

					} catch(IOException ioe) {}
					catch (SAXException e) {
					} catch (ParserConfigurationException e) {
					}
					//catch(SQLException sqle) {}
				}
			});
		}
	}

	private int size = 0; // for getTournamentRoundEntries

	public int getTournamentRoundEntries(int tourn_id, int event_id) throws XMLStreamException, IOException, SAXException, ParserConfigurationException {
		URL url = new URL("https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser saxParser = factory.newSAXParser();

		DefaultHandler handler = new DefaultHandler() {

			private boolean bballot;

			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if(qName.equalsIgnoreCase("BALLOT"))
					bballot = true;
				if(qName.equalsIgnoreCase("ID") && bballot) {
					bballot = false;
					size++;
				}
			}
		};

		saxParser.parse(url.openStream(), handler);

		int localSize = size;
		size = 0;

		System.out.println(url);
		return localSize;
	}

	private XMLEvent getNextStartElement(XMLEventReader xmlEventReader) throws XMLStreamException {
		XMLEvent event = null;
		do {
			event = xmlEventReader.nextTag();
		} while(xmlEventReader.hasNext() && !event.isStartElement());
		return event;
	}

	public void enterTournament(int tourn_id, int event_id) throws ParserConfigurationException,IOException, SAXException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(new URL("https://www.tabroom.com/api/tourn_published.mhtml?tourn_id=" + tourn_id + "&event_id=" + event_id).openStream());

		// Getting schools
		HashMap<Integer, String> schools = new HashMap<Integer, String>();
		NodeList schoolNodes = doc.getElementsByTagName("SCHOOL");
		for(int i = 0;i<schoolNodes.getLength();i++)
		{
			Node node = schoolNodes.item(i);
			if (node.getChildNodes().getLength() > 1) {
				Element element = (Element) node;
				schools.put(Integer.parseInt(element.getElementsByTagName("ID").item(0).getTextContent()), element.getElementsByTagName("SCHOOLNAME").item(0).getTextContent());
			}
		}

		// Getting competitors
		HashMap<Integer, Debater> competitors = new HashMap<Integer, Debater>();
		NodeList entryNodes = doc.getElementsByTagName("ENTRY");
		for(int i = 0;i<entryNodes.getLength();i++)
		{
			try {
				Node node = entryNodes.item(i);
				if (node.getChildNodes().getLength() > 1) {
					Element element = (Element) node;
					Debater debater = new Debater(element.getElementsByTagName("FULLNAME").item(0).getTextContent(), schools.get(Integer.parseInt(element.getElementsByTagName("SCHOOL").item(0).getTextContent())));
					debater.setID(DebateHelper.getDebaterID(sql, debater));
					competitors.put(Integer.parseInt(element.getElementsByTagName("ID").item(0).getTextContent()), debater);
				}
			} catch(SQLException e) {}
		}

		// Getting judges
		HashMap<Integer, Debater> judges = new HashMap<Integer, Debater>();
		NodeList judgesNodes = doc.getElementsByTagName("JUDGE");
		for(int i = 0;i<judgesNodes.getLength();i++)
		{
			Node node = judgesNodes.item(i);
			if(node.getChildNodes().getLength() > 1) {
				Element element = (Element) node;
				Debater debater = new Debater(element.getElementsByTagName("FIRST").item(0).getTextContent() + " " + element.getElementsByTagName("LAST").item(0).getTextContent(), schools.get(Integer.parseInt(element.getElementsByTagName("SCHOOL").item(0).getTextContent())));
				judges.put(Integer.parseInt(element.getElementsByTagName("ID").item(0).getTextContent()), debater);
			}
		}

		// Getting round keys / names
		HashMap<Integer, Character> round = new HashMap<Integer, Character>();
		NodeList roundNodes = doc.getElementsByTagName("ROUND");

		ArrayList<RoundInfo> roundNumbers = new ArrayList<RoundInfo>();
		for(int i = 0;i<roundNodes.getLength();i++)
		{
			Node node = roundNodes.item(i);
			if(node.getChildNodes().getLength() > 1) {
				Element element = (Element) node;
				RoundInfo roundInfo = new RoundInfo();
				roundInfo.number = Integer.parseInt(element.getElementsByTagName("RD_NAME").item(0).getTextContent());
				roundInfo.elim = element.getElementsByTagName("PAIRINGSCHEME").item(0).getTextContent().equals("Elim");
				roundNumbers.add(roundInfo);
			}
		}
		Collections.sort(roundNumbers, new Comparator<RoundInfo>(){
			public int compare(RoundInfo o1, RoundInfo o2){
				return o1.number - o2.number;
			}
		});



	}
}
