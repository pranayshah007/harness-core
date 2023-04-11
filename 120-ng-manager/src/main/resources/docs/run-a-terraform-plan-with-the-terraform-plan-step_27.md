# Export JSON representation of Terraform Plan

Enable this setting to use a JSON representation of the Terraform plan that is implemented in a Terraform Plan step.

In subsequent **Execution** steps, such as a [Shell Script](../../cd-execution/cd-general-steps/using-shell-scripts.md) step, you can reference the Terraform plan using this expression format:

`<+execution.steps.[Terraform Plan step Id].plan.jsonFilePath>`

For example, if you had a Terraform Plan step with the [Id](../../../platform/20_References/entity-identifier-reference.md) `Plan_Step`, you could use the expression in a Shell Script step like this:


```bash
cat "<+execution.steps.Plan_Step.plan.jsonFilePath>"
```

If the Terraform Plan step is located in **Dynamic Provisioning** steps in **Infrastructure**, and the Terraform Plan step Id is `TfPlan`, then expression is:

`<+infrastructure.infrastructureDefinition.provisioner.steps.TfPlan.plan.jsonFilePath>`

For information on Terraform Plan in the **Dynamic Provisioning** steps in **Infrastructure**, see [Provision Target Deployment Infra Dynamically with Terraform](../../cd-infrastructure/terraform-infra/provision-infra-dynamically-with-terraform.md).JSON representation of Terraform plan can be accessed across different stages as well. In this case, the FQN for the step is required in the expression.

For example, if the Terraform Plan step with the Id `TfPlan` is in the **Execution** steps of a stage with the Id `TfStage`, then the expression is like this:

`<+pipeline.stages.TfStage.spec.execution.steps.TfPlan.plan.jsonFilePath>`
