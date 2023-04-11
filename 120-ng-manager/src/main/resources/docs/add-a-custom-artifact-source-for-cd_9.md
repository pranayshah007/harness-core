## Version

Enter the version number for this deployment, or select Runtime Input or Expression to select the version dynamically.

For more information on Fixed Value, Runtime Input, and Expression got to [Fixed Values, Runtime Inputs, and Expressions](../../../platform/20_References/runtime-inputs.md).

When you done Artifact Details will look something like this:

![](./static/add-a-custom-artifact-source-for-cd-08.png)

Later, you can reference the Version using this expression format, which will resolve to the version pulled from the array at runtime:

`<+pipeline.stages.[stage_Id].spec.serviceConfig.output.artifactResults.primary.version>`
