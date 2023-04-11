## Name

1. In **Name**, enter a name for the step, for example, **plan**.

Harness will create an [Entity Id](../../../platform/20_References/entity-identifier-reference.md) using the name. The Id is very important. It's used to refer to settings in this step.

For example, if the Id of the stage is **terraform** and the Id of the step is **plan**, and you want to echo its timeout setting, you would use:

`<+pipeline.stages.Terraform.spec.infrastructure.infrastructureDefinition.provisioner.steps.plan.timeout>`
