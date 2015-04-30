This script (pdml2neo.py) loads network traffic from a pdml file into a neo4j graph.  Usage:
0. Get you neo4j-community-2.1.6 - other version may or may not work with or without tweaking
```
	$ wget 'http://neo4j.com/artifact.php?name=neo4j-community-2.1.6-unix.tar.gz'
	$ mv 'artifact.php?name=neo4j-community-2.1.6-unix.tar.gz' neo4j-community-2.1.6-unix.tar.gz
	$ tar xvfz neo4j-community-2.1.6-unix.tar.gz
```
1. Get you a pcap
2. Run `tshark -r packets.pcap -T pdml > packets.pdml`
3. Then make sure the neo4j server is running and run this script
```
	$ /path/to/neo4j-community-2.1.6/bin/neo4j console &
	[1] 67777
	Starting Neo4j Server console-mode...
	...
	...
	$ python pdml2neo.py packets.pdml
	parsing packets.pdml:  done.
	Creating indexes... done.
	G has 1287 nodes and 24421 edges
```
  Note that this script will happily load the same packets into the database over and over...
4. Then stop the neo4j server, and build and fire up the Jung fat app
```
	$ fg 
	/path/to/neo4j-community-2.1.6/bin/neo4j console
	^C[INFO] ShutdownManager$ShutdownHookHandler - JVM shutdown hook called
	...
	...
	$ export CLASSPATH=.:`echo lib/*jar | sed 's/jar /jar:/g'`
	$ find net -name \*.java -print0 | xargs -0 javac
	$ java net.mariosantana.nwklr.nwklr /path/to/neo4j-community-2.1.6
```
  Revel in the fact that this is a quick POC, and let your imagination run away with all the possibilities.
5. Call me up, or tweet my pinterest on github or something, and offer your enthusiastic help!
