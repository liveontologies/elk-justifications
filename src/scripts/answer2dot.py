#!/usr/bin/python

import sys, re
import argparse
from collections import defaultdict

parser = argparse.ArgumentParser(description='Generate inference graphs highlighting justifications from output of clasp.')
parser.add_argument('-a', '--answersets', default=sys.stdin, help='The file containing output of clasp.')# TODO type=open
parser.add_argument('encoding', help='The clasp input file.')
parser.add_argument('inference_graph', help='The inference graph.')
parser.add_argument('output_dir', help='The output directory.')

LITS = {
	'concl' : 'c',
	'inf' : 'i',
	'conclDepth' : 'c',
	'infDepth' : 'i',
	'axiom' : 'a',
}

GROUP_PRED = 'pred'
GROUP_INDX = 'indx'
GROUP_STRING = 'string'
RE_LITERAL = re.compile(r'\b(?P<%s>%s)\((?P<%s>[^,]+),' % (GROUP_PRED, '|'.join(LITS.keys()), GROUP_INDX))
RE_NUMBER = re.compile(r'\d+')
RE_STRING = re.compile(r'"(?P<%s>[^"]*)"' % (GROUP_STRING))

if __name__ == '__main__':
	
	args = parser.parse_args()
	
	labels = {}
	with open(args.encoding) as encd:
		for line in encd:
			start = 0
			m = RE_LITERAL.search(line[start:])
			while m:
				start += m.end()
				
				nodeId = LITS[m.group(GROUP_PRED)] + m.group(GROUP_INDX)
				
				if m.group(GROUP_PRED) in ('concl', 'axiom'):
					label_match = RE_STRING.search(line[start:])
					labels[nodeId] = label_match.group(GROUP_STRING)
				
				m = RE_LITERAL.search(line[start:])
	
	ansN = 0
	
	inp = sys.stdin# TODO: use the argument !!!
	for line in inp:
		
		print line,
		
		if line.startswith("Answer:"):
			ansN += 1
			
			answer = inp.next()
			
			print answer,
			
			with open(args.output_dir + ('/inferenceGraphForAnswer%s.dot' % ansN), 'w') as out:
				out.write('digraph {\n')
				with open(args.inference_graph) as infG:
					for l in infG:
						out.write(l)
				
				depths = defaultdict(list)
				
				start = 0
				m = RE_LITERAL.search(answer[start:])
				while m:
					start += m.end()
					
					nodeId = LITS[m.group(GROUP_PRED)] + m.group(GROUP_INDX)
					
					if m.group(GROUP_PRED) in ('concl', 'inf', 'axiom'):
						out.write('%s [style=filled];\n' % nodeId)
						if m.group(GROUP_PRED) == 'axiom':
							print m.group()
					
					if m.group(GROUP_PRED) in ('conclDepth', 'infDepth'):
						depth_match = RE_NUMBER.search(answer[start:])
						depths[nodeId].append(int(depth_match.group()))
					
					m = RE_LITERAL.search(answer[start:])
				
				for nodeId, label in labels.items():
					depth_list = depths[nodeId]
					out.write('%s [label="%s %s"];\n' % (nodeId, label, depth_list))
					if nodeId.startswith(LITS['axiom']):
						out.write('%s [color="red"];\n' % (nodeId))
				
				out.write('}\n')
	
	pass

