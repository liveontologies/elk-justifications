#!/usr/bin/env Rscript


#library(tikzDevice)
#tikz(file="plot-results.tex", width=5, height=5)


column <- "time"
xlim <- c(0,100)
ylim <- c(0.001,100)
mergeBy <- "query"


args <- commandArgs(TRUE)

requiredArgCount <- 3
if(length(args) < requiredArgCount) {
	cat(sprintf("Expected %d arguments!\n", requiredArgCount))
	q(status=1)
}


xvalues = list()
yvalues = list()
colors = list()

argIndex <- 1
title <- args[argIndex]

argIndex <- argIndex + 1
legend <- args[argIndex]
argIndex <- argIndex + 1
X <- read.csv(args[argIndex])

dataIndex <- 1
names(X) <- paste0(names(X), ".", dataIndex)
M <- X

while (argIndex + 1 < length(args)) {
	argIndex <- argIndex + 1
	legend <- c(legend, args[argIndex])
	argIndex <- argIndex + 1
	X <- read.csv(args[argIndex])

	dataIndex <- dataIndex + 1
	names(X) <- paste0(names(X), ".", dataIndex)
	M <- merge(M, X, by.x=paste0(mergeBy, ".1"), by.y=paste0(mergeBy, ".", dataIndex))
}

min.na.rm <- function(...) {
	min(..., na.rm=TRUE)
}
M[[column]] <- apply(M[,paste0(column, ".", seq(dataIndex))], 1, min.na.rm)
data <- M[[column]]
timeOrder <- order(data)
step <- 100 / length(data)
xvalues <- seq(step, 100, step)
plot(xvalues, data[timeOrder] / 1000, type="l", col=1, lty=1, log="y", main=title, xlab="\\% of queries", ylab="time in seconds", xlim=xlim, ylim=ylim)

colorIndex <- 2
legendStyle <- c()
for (i in seq(dataIndex)) {
	data <- M[[paste0(column, ".", i)]]
	timeOrder <- order(data)
	step <- 100 / length(data)
	xvalues <- seq(step, 100, step)
	lines(xvalues, data[timeOrder] / 1000, col=colorIndex, lty=colorIndex)

	legendStyle <- c(legendStyle, colorIndex)
	colorIndex <- colorIndex + 1
}

legend <- c(legend, "minimal")
legendStyle <- c(legendStyle, 1)
legend(xlim[1], ylim[2], legend, col=legendStyle, lty=legendStyle)


dev.off()

