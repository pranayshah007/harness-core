# Step 7: Select Duration

In **Duration**, select the number of data points Harness uses. If you enter 10 minutes, Harness will take the first 10 minutes worth of the log/APM data points and analyze it.

The length of time it takes Harness to analyze the 10 min of data points depends on the number of instances being analyzed and the monitoring tool. If you have a 1000 instances, it can take some time to analyze the first 10 minutes of all of their logs/APM data points.

The recommended **Duration** is **10 min** for logging providers and **15 min** for APM and infrastructure providers.

Harness waits 2-3 minutes to allow enough time for the data to be sent to the verification provider before it analyzes the data. This wait time is a standard with monitoring tools. So, if you set the **Duration** to 10 minutes, the 10 minutes excludes the initial 2-3 minute wait, but the total sample time is 13 minutes.
