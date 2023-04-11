# Use tags to revert a failed pipeline execution

When you create a new pipeline stage, add the following tag with runtime input, `<+input>` to the pipeline YAML.

```
tags:
  revert_execution_id: <+input>
```
:::note
Harness UI doesn't support runtime inputs, `<+input>` for tags. Select the YAML view to add runtime inputs to tags. 
:::

Currently, Harness does not measure regressions or failures that occur after a production deployment is complete. 
 
During the pipeline deployment, if there is a downtime, the `revertPipeline` execution restores the service. This allows you to mark a pipeline execution as a restored pipeline and link it to the pipeline execution that introduced the issue.

Below is an example `Inputs.yaml` of a pipeline execution. The `revert_execution_id` tag represents the execution ID of the pipeline that caused the issue.

```
pipeline:
  identifier: "DOra_pipeline"
  tags:
    revert_execution_id: "Q0bizp0QTM6xtB1FZsR0zQ"
  stages:
  - stage:
      identifier: "stage1"
      type: "Deployment"
      spec:
        service:
          serviceRef: "Ser2"
        environment:
          environmentRef: "Env2"
          infrastructureDefinitions:
          - identifier: "Infra_2"
```
