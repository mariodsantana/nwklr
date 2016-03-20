# nwklr is the NetWitness Killer

LOL.  But the idea here is to enable analysts to browse through network traffic
in a graph-oriented fashion.  I've been developing this in Eclipse, so there
are appropriate dot-files for that if you want to dig into the code.

This approach of a fat client backed by neo4j has its benefits:

  - Powerful graph query language to select what's displayed.

  - Simple security, since you only have to protect simple access to the data,
    without supporting complex operations on the server.

  - Offline analysis, especially useful in airgapped environments where a
    simple data import into the work environment is much easier to trust than
    a complex client/server app.

But I think there are better approaches out there:

  - D3, though I don't think a D3 graph can hold as many nodes/edges as this,
    it is much easier to find talent to work on, say Node+Arango+Angular, than
    these ancient Java libraries.

  - Sqrrl.  I've never actually used it, but it looks awesome and I'd love to
    take it out for a spin sometime.

If you're still interested and want to get this thing up and running, here are
some instructions that should get you pretty close without much fuss.

1. Get you neo4j-community-2.1.6 - other version may or may not work with or
   without tweaking
  ```
	$ wget 'http://neo4j.com/artifact.php?name=neo4j-community-2.1.6-unix.tar.gz'
	$ mv 'artifact.php?name=neo4j-community-2.1.6-unix.tar.gz' neo4j-community-2.1.6-unix.tar.gz
	$ tar xvfz neo4j-community-2.1.6-unix.tar.gz
  ```

2. Get you a pcap (or just use the dataset.pcap included here)

3. Run `tshark -r dataset.pcap -T pdml > dataset.pdml`  (or just use the
   dataset.pdml included here)

4. Then make sure the neo4j server is running and run the pdml2neo script
   (after installing prerequisites)
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
  Revel in the fact that this is a quick POC, and let your imagination run away
  with all the possibilities.

6. If you find this interesting, feel free to reach out with project ideas, or
   just to chat about the topic.  I'm always looking for new perspectives on
   security operations.
