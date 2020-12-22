# Debate Rank Server

Debate Rank is a free and open source program that scrapes joyoftournaments and tabroom for round results. 


## Requirements
* JDK 8+
* Maven
* **Postgres server defined in `resources/hibernate.properties` (copy template `hibernate.properties.postgres`)**

## Installation/Run Instructions
To install use
1. `git clone https://github.com/michealrm/DebateRankServer`
2. `cd DebateRankServer`
3. `mvn install`

To run use
1. `mvn compile`
2. `mvn exec:java -Dexec.mainClass="net.debaterank.server.Server"`

## TODO
* Use last tournamennt to determine HS/College status in rating table
