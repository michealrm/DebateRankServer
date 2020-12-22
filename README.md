# Debate Rank Server

Debate Rank is a free and open source program that scrapes joyoftournaments and tabroom for debater results. 

To run use `mvn install`, `mvn compile`, `mvn exec:java -Dexec.mainClass="net.debaterank.server.Server"`

## Requirements
* JDK 8+
* Maven
* Postgres server defined in resources/hibernate.properties (copy hibernate.properties.postgres)

## TODO
* Use last tournamennt to determine HS/College status in rating table
