## What information is available to capture?

Any Jenkins job information in the particular shell session of the pipeline can be captured and output using one or more Jenkins steps. In addition, you can capture information available using the built-in Harness variables. For more information, go to [Built-in and Custom Harness Variables Reference](../../../platform/12_Variables-and-Expressions/harness-variables.md).

Capturing and exporting output in the Jenkins step can be very powerful. For example, a Jenkins step could capture Jenkins build information, and a Harness service could echo the build information and use it in a complex function, and then export the output down the pipeline for further evaluation.
