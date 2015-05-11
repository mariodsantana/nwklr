#!/usr/bin/python
# mds.20150511
# Export a pdml file as json

import xml.sax as sax
import sys
import os.path
import glob
import json

"""
Straight translation from PDML to JSON.
Each packet is a dict of fields as name/value pairs.
Each PDML file is an array of such dicts.
"""
class pdml2neoContentHandler(sax.ContentHandler):
	pktProps = False # dict() to gather up all the props as we parse the PDML for this packet
	packets = False # array of pktProps

	'''
	Utility function adds name/value pair to pktProps, or name/array for names with multiple values
	'''
	def addPktProp(self,name,value):
		if name not in self.pktProps:
			self.pktProps[name] = value
		elif type(self.pktProps[name]) == type(list()):
			self.pktProps[name].append(value)
		else:
			self.pktProps[name] = [self.pktProps[name],value]

	'''
	Process the opening tag of an XML element
	'''
	def startElement(self,name,attrs):
		#if self.pktProps and int(self.pktProps["num"],16) > 10: return # DEBUG let's just do the first 10 packets for now
		# push the parsley aside
		if (name in ("pdml","proto")): return
		# fresh new packet, fresh new state variables
		if (name == "packet"):
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
			self.addPktProp(attrs["name"],"0x"+attrs["value"])
		elif ("show" in attrs):
			# prefer a showable value if available
			self.addPktProp(attrs["name"],attrs["show"])
		elif ("value" in attrs):
			# if no showable value, use hex value - prefer it because "show" isn't defined to be hex, could be anything
			#self.addPktProp(attrs["name"],int(attrs["value"],16) # XXX let's keep this value in hex for now, keep the 'name' prop as a string)
			self.addPktProp(attrs["name"],"0x"+attrs["value"])
		else:
			print "field {0} has no value or show attributes:\n\tattribute keys: {1}".format(attrs["name"],attrs.keys())
			sys.exit()
		twirl()
	# END def startElement

	'''
	Process the closing tag of an XML element
	'''
	def endElement(self,name):
		# we only need to process end-of-packet
		if (name != "packet"): return
		# get local pointers to instance vars, using self. gets annoying
		self.packets.append(self.pktProps)
	# END def endElement

	def __init__(self):
		self.pktProps = {}
		self.packets = []
	def getPackets(self):
		return self.packets
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
outfile = infile[0:infile.rfind(".")]
outfile += ".json"
print "writing json to {0}".format(outfile),
json.dump(CH.getPackets(),open(outfile,"w"))
print "done."