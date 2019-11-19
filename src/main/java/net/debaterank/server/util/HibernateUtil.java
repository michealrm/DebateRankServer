package net.debaterank.server.util;

import net.debaterank.server.models.Debater;
import net.debaterank.server.models.Judge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.util.List;

public class HibernateUtil {

	private static Logger log = LogManager.getLogger(HibernateUtil.class);
	private static StandardServiceRegistry ssr;
	private static Metadata md;
	public static SessionFactory sf;

	static {
		buildSessionFactory();
	}

	private static void buildSessionFactory() {
		ssr = new StandardServiceRegistryBuilder().configure("hibernate.cfg.xml").build();
		md = new MetadataSources(ssr).getMetadataBuilder().build();
		sf = md.getSessionFactoryBuilder().build();
		log.info("Built session factory");
	}

	public static Session getSession() {
		if(sf == null) buildSessionFactory();
		return sf.openSession();
	}

	public static void loadCache() {
		// TODO: Base this off of the config
		List<Debater> debaters = Debater.getDebaters();
		List<Judge> judges = Judge.getJudges();
	}

}