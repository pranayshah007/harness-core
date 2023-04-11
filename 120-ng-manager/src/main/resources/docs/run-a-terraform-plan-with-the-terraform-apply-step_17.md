## Inline Variables

You can add inline variables just like you would in a tfvar file.

Click **Add Terraform Var File**, and then click **Add Inline**.

The **Add Inline Terraform Var File** settings appear.

In **Identifier**, enter an identifier so you can refer to variables using expressions if needed.

For example, if the **Identifier** is **myvars** you could refer to its content like this:

`<+pipeline.stages.MyStage.spec.infrastructure.infrastructureDefinition.provisioner.steps.plan.spec.configuration.varFiles.myvars.spec.content>`

Provide the input variables and values for your Terraform script. Harness follows the same format as Terraform.

For example, if your Terraform script has the following:


```json
variable "region" {  
  type = string  
}
```
In **Add Inline Terraform Var File**, you could enter:


```json
region = "asia-east1-a"
```
