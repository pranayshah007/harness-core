# Terraform Apply Step

The Terraform Apply step simply inherits its configuration from the Terraform Plan step you already configured.

As stated earlier, you use the same **Provisioner Identifier** in the Terraform Plan and Terraform Apply steps:

![](./static/provision-infra-dynamically-with-terraform-05.png)

1. In **Terraform Apply**, enter a name for the step. The name is very important, as you'll use it to select the outputs from the Terraform apply operation.
   
   You'll use the outputs when mapping the provisioned infrastructure to the target Infrastructure Definition.
   For example, if you name the Terraform Apply step **apply123** and you want to reference the **region** output in your script, you'd use the expression:
   `<+infrastructureDefinition.provisioner.steps.apply123.output.region>`
   The mapping is explained in the next step.
2. In **Timeout**, enter how long Harness should wait to complete the Terraform Apply step before failing the step.
3. In **Configuration Type**, select **Inherit From Plan**. If you select **Inline**, then you aren't using the previous Terraform Plan step. You are entering separate Terraform files and settings.
4. In **Provisioner Identifier**, enter the same Provisioner Identifier you entered in the Terraform Plan step.
5. Click **Apply Changes**.
