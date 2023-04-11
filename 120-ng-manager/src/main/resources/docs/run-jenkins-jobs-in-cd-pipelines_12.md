# Captured environment variables from Jenkins builds

For Harness to capture Jenkins environment variables, your Jenkins configuration requires the [EnvInject plugin](https://wiki.jenkins.io/display/JENKINS/EnvInject+Plugin). The plugin does not provide full compatibility with the pipeline plugin. Go to [known incompatibilities](https://wiki.jenkins.io/display/JENKINS/EnvInject+Plugin#EnvInjectPlugin-Knownincompatibilities) from Jenkins for more information. Harness captures certain environment variables from the Jenkins build.

The following list shows examples of the environment variables and the expressions you can use to reference them.

* **Job Status:** `<+execution.steps.[step_Id].build.executionStatus>`
* **Job URL:** `<+execution.steps.[step_Id].build.jobUrl>`
* **Build number:** `<+execution.steps.[step_Id].build.buildNumber>`
* **Build display name:** `<+execution.steps.[step_Id].build.buildDisplayName>`
* **Full build display name:** `<+execution.steps.[step_Id].build.buildFullDisplayName>`

Here's a sample script.

```
echo "Job Status:" <+execution.steps.Jenkins_Build.build.executionStatus>
echo "Job URL:" <+execution.steps.Jenkins_Build.build.jobUrl>
echo "Build number:" <+execution.steps.Jenkins_Build.build.buildNumber>
echo "Build display name:" <+execution.steps.Jenkins_Build.build.buildDisplayName>
echo "Full build display name:" <+execution.steps.[step_Id].build.buildFullDisplayName>
```

:::note

If you are using [step groups](../../cd-technical-reference/cd-gen-ref-category/step-groups.md) the expressions must include the step group Ids also.

For example, `<+execution.steps.[step group Id].steps.[step Id].build.jobUrl>`.

:::

You can reference this job information in subsequent steps in your pipeline or in another pipeline.

To reference the job information in another stage in this pipeline, use the full pipeline path to the build information. For example, `<+pipeline.stages.[stage_Id].spec.execution.steps.[step_Id].build.executionStatus>`.
