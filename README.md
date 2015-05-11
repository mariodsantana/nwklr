This script (pdml2neo.py) loads network traffic from a pdml file into a neo4j graph.  Usage:

1. Get you neo4j-community-2.1.6 - other version may or may not work with or without tweaking
  ```
	$ wget 'http://neo4j.com/artifact.php?name=neo4j-community-2.1.6-unix.tar.gz'
	$ mv 'artifact.php?name=neo4j-community-2.1.6-unix.tar.gz' neo4j-community-2.1.6-unix.tar.gz
	$ tar xvfz neo4j-community-2.1.6-unix.tar.gz
  ```

2. Get you a pcap (or just use the dataset.pcap included here)
3. Run `tshark -r dataset.pcap -T pdml > dataset.pdml`  (or just use the dataset.pdml included here)
4. Then make sure the neo4j server is running and run this script (after installing prerequisites)
  ```
	$ /path/to/neo4j-community-2.1.6/bin/neo4j console &
	[1] 67777
	Starting Neo4j Server console-mode...
	...
	...
	$ sudo pip install py2neo
	Downloading/unpacking py2neo
	  Downloading py2neo-2.0.6.tar.gz (251kB): 251kB downloaded
	...
	...
	Successfully installed py2neo
	Cleaning up...
	$ python pdml2neo.py dataset.pdml
	About to parse dataset.pdml, shall I NUKE THE DATABASE first? (Y/n) 
	 OK, nukes away... done.
	parsing dataset.pdml:  done.
	Creating indexes... done.
	G has 69 nodes and 1800 edges
	done.
 ```

5. Then stop the neo4j server, and build and fire up the Jung fat app
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

6. Call me up, or tweet my pinterest on github or something, and offer your enthusiastic help!
