#!/usr/bin/env Rscript


#library(tikzDevice)
#tikz(file="plot-results.tex", width=5, height=5)


column <- "time"
xlim <- c(0,100)
ylim <- c(0.001,100)


args <- commandArgs(TRUE)

requiredArgCount <- 3
if(length(args) < requiredArgCount) {
	cat(sprintf("Expected %d arguments!\n", requiredArgCount))
	q(status=1)
}


title <- args[1]
index <- 2
legend <- args[index]
index <- index + 1
X <- read.csv(args[index])
colorIndex <- 2
legendStyle <- colorIndex


timeOrder <- order(X[[column]])
step <- 100 / length(X[[column]])
xvalues <- seq(step, 100, step)

plot(xvalues, X[[column]][timeOrder] / 1000, type="l", col=colorIndex, lty=colorIndex, log="y", main=title, xlab="\\% of queries", ylab="time in seconds", xlim=xlim, ylim=ylim)

while (index + 1 < length(args)) {
	index <- index + 1
	legend <- c(legend, args[index])
	index <- index + 1
	colorIndex <- colorIndex + 1
	legendStyle <- c(legendStyle, colorIndex)

	X <- read.csv(args[index])
	timeOrder <- order(X[[column]])
	step <- 100 / length(X[[column]])
	xvalues <- seq(step, 100, step)

	lines(xvalues, X[[column]][timeOrder] / 1000, col=colorIndex, lty=colorIndex)

}


legend(xlim[1], ylim[2], legend, col=legendStyle, lty=legendStyle)


dev.off()

