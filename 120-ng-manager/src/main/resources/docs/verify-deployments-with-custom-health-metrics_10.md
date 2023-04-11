# Step 7: Select Duration

Select how long you want Harness to analyze and monitor the APM data points. Harness waits for 2-3 minutes to allow enough time for the data to be sent to the APM tool before it analyzes the data. This wait time is standard with monitoring tools.

The recommended **Duration** is **15 min** for APM and infrastructure providers.### Step 8: Specify Artifact Tag

In **Artifact Tag**, use a [Harness expression](../../../platform/12_Variables-and-Expressions/harness-variables.md) to reference the artifact in the stage Service settings.

The expression `<+serviceConfig.artifacts.primary.tag>` refers to the primary artifact.
