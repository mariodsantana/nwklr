#!/usr/bin/python
# mds.20150205
# Import a pdml file into a neo4j database
"""
--------------------[ README
This script (pdml2neo.py) loads network traffic from a pdml file into a neo4j graph.  Usage:
1. Get you a pcap
2. Run tshark -r packets.pcap -T pdml > packets.pdml
3. Then make sure the neo4j server is running and run this script
	$ /path/to/neo4j console &
	[1] 67777
	Starting Neo4j Server console-mode...
	...
	...
	$ python pdml2neo.py /path/to/packets.pdml
	parsing /path/to/packets.pdml:  done.
	Creating indexes... done.
	G has 1287 nodes and 24421 edges
	Writing G in Geoff to /path/to/packets.geoff...  done.
		----[ Note that this script will happily load the same packets into the database over and over...
4. Then stop the neo4j server and fire up the Jung fat app
	$ fg 
	/path/to/neo4j console
	^C[INFO] ShutdownManager$ShutdownHookHandler - JVM shutdown hook called
	...
	...
	$ java -jar /path/to/nwklr.jar
		----[ Revel in the fact that this is a quick POC, and let your imagination run away with all the possibilities
5. Call me up, or tweet my pinterest on github or something, and offer your enthusiastic help!
--------------------[ /README
"""

from py2neo import Graph, Node, Relationship
from py2neo.ext.geoff import GeoffWriter
import xml.sax as sax
import sys
import os.path
import glob
import json

"""
Nodes will be addresses (eth/IP/etc) for sender and recipient
Edges will be comms (packets or streams) between nodes
	also have "aka" nodes linking addresses that have been seen in the same packet (e.g., in the eth & IP addresses) 

for packets, we end up with (src)-[:pktTo]->(dst)
	each such edge contains the props of all packet field name/value pairs
	as well as a prop named inStr with the neo4j url of the strTo (see below) of which it is a part
TODO some fields are duped (e.g., ip.addr) and should be an array of vals
for streams, we end up with (src)-[:strTo]->(dst)
each such edge links to all of the packets sent in that stream, like this:
	the initiator of a stream is probably the src address in the earliest packet of that stream
	in fact, a stream might only contain packets heading in one direction...
(src) and (dst) - the graph nodes - are hosts.
	They are identified by address and type, e.g., name="192.168.1.1" type="ip" or name="00ae5c:8b94f2" type="eth"
	XXX - make sure the "type" property is actually created...seems to be missing?
If a packet identifies multiple addresses (e.g., both eth and ip) then two nodes are created:
	but the highest level address (.e.g, ip instead of eth) "wins" and get the pktTo and/or strTo relationships
	and each address level is linked to the lower address level with an "aka" relationship
		this way, you can see that the ARP request came from the same host as the DNS request.

TODO
1. Build a poc gui for hunting through those graphs - IN PROCESS
2. Implement UDP stream tracking.  Also ICMP, BOOTP, others
3. Consider higher-level address nodes, like http.server and http.user-agent, and attaching these to other nodes via "aka" edges
4. Make different kinds of edges, beyond just data transfer and "aka"
		e.g., hosts/nodes can point to the most recent DNS query that contained that addr in the resp
		and hell, DNS queries can point to the previous query or resp that contained the same name (in case DNS resps change for the same query)
	e.g., nodes with matching ethernet and IP address can point to each other
5. Allow for creation of a new graph where the nodes are any two packet or stream props (or both can be the same props)
	and the edges show comms between props.  e.g., the props could be http.server and http.user_agent
6. Explore interesting concepts
		What if all props are nodes themselves?  We could more easily visualize what hosts/vertices and edges/comms share prop values.
"""
class pdml2neoContentHandler(sax.ContentHandler):
	G = False # neo4j graph
	# A couple of state variables to gather packet data before adding the packet as an edge
	srcN = False # Node of this packet's sender
	dstN = False # Node of the packets's destination
	pktProps = False # dict() to gather up all the props as we parse the PDML for this packet
	unstreamedProtocols = False # dict of frame.protocol entries for which we don't create a stream.  value is count of pkts with that frame.protocol
	def startElement(self,name,attrs):
		#if self.pktProps and int(self.pktProps["num"],16) > 10: return # DEBUG let's just do the first 10 packets for now
		# push the parsley aside
		if (name in ("pdml","proto")): return
		# fresh new packet, fresh new state variables
		if (name == "packet"):
			self.srcN = False
			self.dstN = False
			self.pktProps = dict()
			return # <packet> has no actual info...
		# This is necessarily a field element........right?
		if (name != "field"):
			print "We skipped pdml, proto, and packet elements, and now we're in a non-'field' element?"
			print "\tElement name: {}".format(name)
			print "\tElement attr keys: {}".format(attrs.keys())
			sys.exit()
		# populate the pktProps dict with this new field info!
		if (not len(attrs["name"])): # blank names seem to show up in TCP options, and in GeoIP fields - not useful in my first test data
			pass # TODO add processing for the GeoIP stuff, check into the TCP options stuff
		elif (attrs["name"] == "expert" or attrs["name"] == "_ws.expert"): # same field, different names in different wireshark versions
			pass # this isn't really a "field" - it only exists to group some real fields
		elif (attrs["name"] == "data"):
			# value is the data passed, encoded as a hex byte string TODO should we convert to raw bytes, or let the gui have that option?
			self.pktProps[attrs["name"]] = "0x"+attrs["value"]
		elif ("show" in attrs):
			# prefer a showable value if available
			self.pktProps[attrs["name"]] = attrs["show"]
		elif ("value" in attrs):
			# if no showable value, use hex value - prefer it because "show" isn't defined to be hex, could be anything
			#self.pktProps[attrs["name"]] = int(attrs["value"],16) # XXX let's keep this value in hex for now, keep the 'name' prop as a string
			self.pktProps[attrs["name"]] = "0x"+attrs["value"]			
		else:
			print "field {0} has no value or show attributes:\n\tattribute keys: {1}".format(attrs["name"],attrs.keys())
			sys.exit()
		# If this is an addressing field, create host/node
		# Highest level address wins - i.e., prefer IP addr over eth addr - because pdml presents them in that order
		#    TODO - verify there are no exceptions to this
		addrType = False
		aka = False
		if (attrs["name"] == "eth.src"):
			addrType = ("eth","src")
		elif (attrs["name"] == "eth.dst"):
			addrType = ("eth","dst")
		elif (attrs["name"] == "ip.src"):
			addrType = ("ip","src")
		elif (attrs["name"] == "ip.dst"):
			addrType = ("ip","dst")
		else: return # not an address we understand
		if addrType[1] == "src":
			aka = self.srcN
		elif addrType[1] == "dst":
			aka = self.dstN
		nodes = []
		for node in self.G.merge("Host",property_key="name",property_value=attrs["show"]):
			nodes.append(node)
		if len(nodes) != 1:
			print "Found (not 1) things when merging a host/Node:"
			print "\telement {}: {}".format(name,attrs)
			print "\tthings found: {}".format(nodes)
			sys.exit()
		if 'type' not in node.properties:
			# if the merge command created this host/node, there would be no type prop.  let's add it
			node.properties['type'] = addrType[0]
			node.properties.push()
		elif node.properties['type'] != addrType[0]:
			print "got an existing host/node with a wrong type property: {}".format(node)
			print("\t{}: {}".format(k,v) for k,v in node.properties.items())
			sys.exit()
		if addrType[1] == "src": self.srcN = node
		elif addrType[1] == "dst": self.dstN = node
		else: raise(Exception("sanity check failed: addrType isn't src or dst"))
		if aka: # this host/node had a lower-level address, let's link the two host/nodes
			# does this aka/Relationship already exist in the db?
			akas = []
			for match in self.G.match(node,"aka",aka):
				akas.append(match)
			if len(akas) == 0:
				# Nope, let's create the aka/Relationship
				akaR = Relationship.cast(node,("aka",{"first_time":self.pktProps["timestamp"],"last_time":self.pktProps["timestamp"]}),aka)
				self.G.create_unique(akaR)
			elif len(akas) == 1:
				# Yep, just update the last_time property
				akas[0]["last_time"] = self.pktProps["timestamp"]
				akas[0].push()
			else:
				print "Found multiple things when merging an aka/Relationship:"
				print "\telement {}: {}".format(name,attrs)
				print "\tthings found: {}".format(akas)
				sys.exit()
	# END def startElement

	def endElement(self,name):
		# we only need to process end-of-packet
		if (name != "packet"): return
		# get local pointers to instance vars, using self. gets annoying
		G = self.G
		s = self.srcN
		d = self.dstN
		p = self.pktProps
		#if int(p["num"],16) > 10: return # DEBUG let's just do the first 10 packets for now
		twirl()
		# if we don't have node/address information by now, we're bonked
		if (not d or not s):
			print "Warning! One or both nodes are blank. '{0}' -> '{1}'".format(s,d)
			print "Props: {}".format(p)
			sys.exit()
		# create packet/edge
		pktToR = Relationship.cast(s,("pktTo", p),d)
		G.create(pktToR)
		# is this packet part of a stream?
		strType = strID = False
		if ("tcp.stream" in p): # it's a TCP stream!
			strType = "tcp.stream"
			# these next few lines sort so strID reflects the same tuple, regardless of traffic direction
			strIDSrc = [ p["ip.src"],p["tcp.srcport"] ]
			strIDDst = [ p["ip.dst"],p["tcp.dstport"] ]
			c = [ strIDSrc, strIDDst ]
			c.sort()
			strID = "ip{0}:tcp{1}<>ip{2}:tcp{3}".format(c[0][0],c[0][1],c[1][0],c[1][1])
		if (not strType):
			# the packet belongs to no protocol for which we know how to follow the stream
			if p["frame.protocols"] in self.unstreamedProtocols:
				self.unstreamedProtocols[p["frame.protocols"]] += 1
			else:
				self.unstreamedProtocols[p["frame.protocols"]] = 1
			return
		# it IS part of a stream!  let's create that stream/edge
		p["name"] = strID
		# does this strTo/Relationship already exist in the db?
		strTos = []
		for match in self.G.match(s,"strTo",d):
			strTos.append(match)
		if len(strTos) == 0:
			# Nope, let's create the strTo/Relationship...
			strToR = Relationship.cast(s,("strTo",{"ID":strID,"first_time":p["timestamp"]}),d)
			strToR["pkt_count"] = 0
			self.G.create_unique(strToR)
		elif len(strTos) == 1:
			# Yep, it already exists
			strToR = strTos[0]
		else:
			print "Found multiple things when merging a strTo/Relationship:"
			print "\tthings found: {}".format(strTos)
			sys.exit()
		# Now it exists, update it
		strToR["pkt_count"] += 1
		for k,v in p.items(): strToR[k]=v
		strToR.push()
		pktToR["inStr"] = strToR.ref
		pktToR.push()
	# END def endElement

	def __init__(self,newG = Graph()):
		self.G = newG
		self.pktProps = self.srcN = self.dstN = False
		self.unstreamedProtocols = dict()
	def getGraph(self):
		return self.G
	def getUnstreamedProtocols(self):
		return self.unstreamedProtocols
#####################################
## END class pdml2nxContentHandler ##
#####################################

###############################
## The MDS twirling protocol ##
###############################
twirls = [ '/', '-', '\\', '|' ]
at_twirl = 0
def twirl():
	global at_twirl
	this_twirl = twirls[at_twirl % len(twirls)]
	last_twirl = twirls[(at_twirl-1) % len(twirls)]
	if at_twirl == 0:
		backup = ""
	else:
		backup = '\b' * (len(last_twirl) + 1)
	print "{}{}".format(backup,this_twirl),
	sys.stdout.flush()
	at_twirl += 1
def del_twirl():
	global at_twirl
	if at_twirl == 0:
		return
	last_twirl = twirls[(at_twirl-1) % len(twirls)]
	backup = '\b' * (len(last_twirl) + 1)
	print "{}{}{}".format(backup,' '*len(last_twirl),backup),
	sys.stdout.flush()
	at_twirl = 0
###############################
## END MDS twirling protocol ##
###############################



P = sax.make_parser()
CH = pdml2neoContentHandler()
P.setContentHandler(CH)
infile = ''
if (len(sys.argv)>1 and os.path.isfile(sys.argv[1])):
	infile = sys.argv[1]
else:
	infile = glob.glob("*.pdml")[0]
if (not len(infile)):
	print "couldn't figure out a file to read..."
	sys.exit()
print "parsing {0}: ".format(infile),
P.parse(infile)
del_twirl()
print "done."


print "Creating indexes...",
sys.stdout.flush()
G = CH.getGraph()
G.cypher.execute('create index on :pktTo(inStr)')
G.cypher.execute('create index on :Host(name)')
print "done."


print "G has {0} nodes and {1} edges".format(G.order,G.size)
#gfile = infile[:infile.rfind('.')]+".geoff"
#print "Writing G in Geoff to {}... ".format(gfile),
#sys.stdout.flush()
#with open(gfile,'w') as g:
#	writer = GeoffWriter(g)
#	writer.write(G.match())
print "done."

# lambda sorts based on peers (sort the peers so whomever is sending, it sorts the same) plus pkt number
# for src,dst,pktData in sorted(pktG.edges(data=True),key=lambda e: sorted(e[:2]).append(e[2]["num"])):
# 	######## Some basic visibility into what was created
# 	print "{0} ---> {1}".format(src,dst)
# 	print "\t{0}".format(pktData)
# 	print"\tNumber: {0}".format(pktData["num"])

# print "\n\n"
# what nodes are in pktG but not in strG?  i.e., what nodes don't participate in any known streams?
# for n in pktG.nodes():
# 	if n not in strG.nodes():
# 		print "{0} has packets but no streams".format(n)
# 		print "\tProtocols participated in by this address:"
# 		prots = dict()
# 		for src,dst,data in pktG.out_edges(nbunch=[n],data=True) + pktG.in_edges(nbunch=[n],data=True):
# 			prots[data["frame.protocols"]] = True
# 		print "\t\t{0}".format(", ".join(prots.keys()))

# print "\n\n"
# print "Unstreamed protocols:"
# for prot,cnt in sorted(CH.getUnstreamedProtocols().items()):
# 	print "\t{0:>8} counts of {1}".format(cnt,prot)


# Some JSON encoding exercises
# print "\n\n"
# jsonNodeLen = jsonEdgeLen = jsonBothLen = 0
# for n in (strG.nodes()+pktG.nodes()):
# 	jsonNodeLen += len(json.dumps(n))
# for src,dst,key,strData in (strG.in_edges(keys=True,data=True) +
# 							strG.out_edges(keys=True,data=True) +
# 							pktG.in_edges(keys=True,data=True) +
# 							pktG.out_edges(keys=True,data=True)):
# 	jsonEdgeLen += len(json.dumps(key))
# 	jsonEdgeLen += len(json.dumps(strData))
# 	jsonBothLen += len(json.dumps(src))
# 	jsonBothLen += len(json.dumps(dst))
# jsonBothLen += jsonEdgeLen
# print "JSON lengths:"
# print "\tnodes: {}".format(jsonNodeLen)
# print "\tedges: {}".format(jsonEdgeLen)
# print "\tboth : {}   (with many duped nodes!".format(jsonBothLen)

