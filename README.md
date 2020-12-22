# Debate Rank Server

Debate Rank is a free and open source program that scrapes joyoftournaments and tabroom for round results. 


## Requirements
* JDK 8+
* Maven
* Postgres server defined in resources/hibernate.properties (copy hibernate.properties.postgres)

## Installation/Run Instructions
To run use
1. `mvn install`
2. `mvn compile`
3. `mvn exec:java -Dexec.mainClass="net.debaterank.server.Server"`

## TODO
* Use last tournamennt to determine HS/College status in rating table
