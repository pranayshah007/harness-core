## Name

In **Name**, enter a name for the step, for example, **apply**.

The name is very important. You can use the name in [expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) to refer to settings in this step.

For example, if the name of the stage is **Terraform** and the name of the step is **apply**, and you want to echo its timeout setting, you would use:

`<+pipeline.stages.Terraform.spec.execution.steps.apply.timeout>`

or simply `<+execution.steps.apply.timeout>`.
