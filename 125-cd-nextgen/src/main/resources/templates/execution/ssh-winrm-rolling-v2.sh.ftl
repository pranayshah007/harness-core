${failureStrategies}
${rollingSnippet
?replace("<+maxConcurrency>", maxConcurrency)
?replace("<+partitionSize>", partitionSize)
?replace("<+unit>", unitType)}