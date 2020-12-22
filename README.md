# Debate Rank Server

Debate Rank is a free and open source program that scrapes joyoftournaments and tabroom for debater results. 

This software requires a postgresql database defined in resources/hibernate.properties. Copy resources/hibernate.properties.postgres to resources/hibernate.properties and fill in the properties accordingly.
To run use `mvn install`, `mvn compile`, `mvn exec:java -Dexec.mainClass="net.debaterank.server.Server"`

TODO:
* Use last tournamennt to determine HS/College status in rating table
