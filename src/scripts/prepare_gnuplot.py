#!/usr/bin/python

import sys
import argparse
import csv
from string import Template

parser = argparse.ArgumentParser(description='Prepare gnuplot script from the supplied data files.')
parser.add_argument('files', nargs='+', help='The data files.')

MIN_Y_RANGE = 0.000001

GNUPLOT_SCRIPT_TEMPLATE = Template("""

reset

set terminal lua tikz latex
set output "plot.tex"

#set title "tau_m"
set style data lines
set key left top
set logscale y
#set tics axis
#shrink = 0.1
set xrange[0:100]
set yrange[${lower_y_range}:${upper_y_range}]
#set xtics shrink/2
#set ytics shrink/2
#set size square
set xlabel "\\\\% of queries"
set ylabel "time in seconds"

plot ${plot_cmd}
${data}
pause -1

""")

if __name__ == "__main__":
	
	args = parser.parse_args()
	
	plot_cmd = ""
	data_string = ""
	min_data = sys.float_info.max
	max_data = sys.float_info.min
	for data_file in args.files:
		
		plot_cmd += """'-' title "%s", """ % data_file
		
		with open(data_file) as fd:
			reader = csv.reader(fd, delimiter=',', quotechar='"')
			header = reader.next()
#			print header
#			time_index = header.index('time')
			time_index = 0
			for h in header:
				if h.find('time') >= 0:
					break
				time_index += 1
			data = []
			for line in reader:
				data.append(float(line[time_index])/1000)
			data.sort()
			
			if data[0] < min_data:
				min_data = data[0]
			if data[len(data) - 1] > max_data:
				max_data = data[len(data) - 1]
			
			step = 100.0/len(data)
			
			x = step
			for d in data:
				data_string += "%f\t%f\n" % (x, d)
				x += step
			data_string += "e\n"
		
		pass
	
	min_data = max(min_data, MIN_Y_RANGE)
	
	print GNUPLOT_SCRIPT_TEMPLATE.substitute(plot_cmd=plot_cmd, data=data_string, lower_y_range=min_data, upper_y_range=max_data)
	
	pass







