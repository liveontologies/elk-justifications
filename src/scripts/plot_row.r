#!/usr/bin/env Rscript


plot.results = function(
		args,
		main=NULL,
		column="time",
		mergeBy="query",
		timeout=60,
		xlim=NULL,
		ylim=c(0.001,timeout),
		from.prop=10,
		to.prop=5) {

	transform.proportions = function(x, from, to) {
		base = 1 + 1/(to - 1)
		base ^ (x / from) * from
	}

	xtrans = function(x) {
		transform.proportions(x, from.prop, to.prop)
	}


	#xlim <- c(xtrans(0),xtrans(100))

	cut.timeout = function(x) {
		sapply(x, function(e) min(e,timeout))
	}


	xvalues = list()
	yvalues = list()
	colors = list()

	argIndex <- 1
	X <- read.csv(args[argIndex])

	dataIndex <- 1
	names(X) <- paste0(names(X), ".", dataIndex)
	M <- X

	while (argIndex < length(args)) {
		argIndex <- argIndex + 1
		X <- read.csv(args[argIndex])

		dataIndex <- dataIndex + 1
		names(X) <- paste0(names(X), ".", dataIndex)
		M <- merge(M, X, by.x=paste0(mergeBy, ".1"), by.y=paste0(mergeBy, ".", dataIndex))
	}

	M[[column]] <- apply(M[,paste0(column, ".", seq(dataIndex))], 1, function(x) min(x, na.rm=TRUE))
	data <- M[[column]]
	timeOrder <- order(data)
	step <- 100 / length(data)
	xvalues <- seq(step, 100, step)
	plot(xtrans(xvalues), cut.timeout(data[timeOrder] / 1000),
			type="l", log="y", axes=FALSE, main=main,
			xlab="\\% of queries", ylab="time in seconds", xlim=xlim, ylim=ylim)
	xticks = seq(0, 100, 10)
	axis(1, at=xtrans(xticks), labels=xticks)
	yticks = c(0.001, 0.01, 0.1, 1, 10, 60, 100)
	ylabels = c("0.001", "0.01", "0.1", "1", "10", "60", "100")
	axis(2, at=yticks, labels=ylabels)
	abline(h=yticks, v=xtrans(xticks), col="gray", lty=3)
	box()

	colorIndex <- 2
	for (i in seq(dataIndex)) {
		data <- M[[paste0(column, ".", i)]]
		timeOrder <- order(data)
		step <- 100 / length(data)
		xvalues <- seq(step, 100, step)
		lines(xtrans(xvalues), cut.timeout(data[timeOrder] / 1000), col=colorIndex, lty=colorIndex)

		colorIndex <- colorIndex + 1
	}

}


separator = "--"

args <- commandArgs(TRUE)

argIndex <- 1
titles = c()
while (argIndex <= length(args)) {
	arg = args[argIndex]
	argIndex = argIndex + 1
	if (arg == separator) {
		break
	}
	titles = c(titles, arg)
}

if (argIndex >= length(args)) {
	cat(sprintf("Missing data arguments!\n"))
	q(status=1)
}
args = args[argIndex:length(args)]
if (length(args) %% length(titles) != 0) {
	cat(sprintf("Not the same number of data per wach title!\n"))
	q(status=1)
}
argArray = array(args, c(length(titles), ceiling(length(args) / length(titles))))


size=5
pdf(width=length(titles)*size, height=size)
#library(tikzDevice)
#tikz(file="plot-results.tex", width=length(titles)*size, height=size)

par(mfrow=c(1, length(titles)))
rowIndex = 1
while (rowIndex <= length(titles)) {
	print(titles[rowIndex])
	print(argArray[rowIndex,])
	plot.results(argArray[rowIndex,], main=titles[rowIndex])
	rowIndex = rowIndex + 1
}


dev.off()

