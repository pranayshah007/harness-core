## Name

In **Name**, enter a name for the step, for example, **plan**.

The name is very important. It's used to refer to settings in this step.

For example, if the name of the stage is **Terraform** and the name of the step is **plan**, and you want to echo its timeout setting, you would use:

`<+pipeline.stages.Terraform.spec.execution.steps.plan.timeout>`
