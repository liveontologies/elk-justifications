#!/usr/bin/env Rscript


plot.results = function(
		args,
		isFirst=TRUE,
		main=NULL,
		column="time",
		mergeBy="query",
		timeout=60,
		xlim=NULL,
		ylim=c(0.001,timeout),
		from.prop=10,
		to.prop=3,
		xlab="\\% of queries",
		ylab="time in seconds",
		colors=c("red", "green3", "blue", "magenta"),
		lineTypes=c("44", "1343", "73", "2262")) {

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

	par(mar=c(0, 0, 0, 0))
	par(mgp=c(-2.2, -1, 0))

	M[[column]] <- apply(M[,paste0(column, ".", seq(dataIndex))], 1, function(x) min(x, na.rm=TRUE))
	data <- M[[column]]
	timeOrder <- order(data)
	step <- 100 / length(data)
	xvalues <- seq(step, 100, step)
	plot(xtrans(xvalues), cut.timeout(data[timeOrder] / 1000),
			type="l", log="y", axes=FALSE,
			xlab="", ylab="", xlim=xlim, ylim=ylim)
	xticks = seq(0, 100, 10)
	axis(1, at=xtrans(xticks), labels=xticks, lty=0)
	yticks = c(0.001, 0.01, 0.1, 1, 10, 60, 100)
	ylabels = c("", "0.01", "0.1", "1", "10", "60", "100")
	axis(2, at=yticks, labels=ylabels, lty=0)
	abline(h=yticks, v=xtrans(xticks), col="gray", lty=3)
	box()
	title(main=main, line=-2)
	title(xlab=xlab, line=-2.2)
	title(ylab=ylab, line=-2.2, adj=0.8)

	colorIndex <- 1
	for (i in seq(dataIndex)) {
		data <- M[[paste0(column, ".", i)]]
		timeOrder <- order(data)
		step <- 100 / length(data)
		xvalues <- seq(step, 100, step)
		lines(xtrans(xvalues), cut.timeout(data[timeOrder] / 1000),
				col=rep(colors, length.out=colorIndex)[colorIndex],
				lty=rep(lineTypes, length.out=colorIndex)[colorIndex],
				lwd=2)

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


size=1.61
pdf(width=length(titles)*size, height=size)
#library(tikzDevice)
#tikz(file="plot-resolution.tex", width=length(titles)*size, height=size)
#tikz(file="plot-resolution.tex", width=length(titles)*size/2, height=size*2)

par(mfrow=c(1, length(titles)))
#par(mfrow=c(2, length(titles)/2))
isFirst = TRUE
rowIndex = 1
while (rowIndex <= length(titles)) {
	print(titles[rowIndex])
	print(argArray[rowIndex,])
	plot.results(argArray[rowIndex,], isFirst=isFirst, main=titles[rowIndex])
	rowIndex = rowIndex + 1
	if (isFirst) {
		isFirst = FALSE
	}
}


dev.off()

