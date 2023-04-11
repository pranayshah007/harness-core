## Provisioner Identifier

1. Enter a unique value in **Provisioner Identifier**.

The Provisioner Identifier identifies the provisioning done in this step. You use the Provisioner Identifier in additional steps to refer to the provisioning done in this step.

The most common use of Provisioner Identifier is between the Terraform Plan and Terraform Apply steps. To have the Terraform Apply step apply the provisioning from this Terraform Plan step, you use the same Provisioner Identifier in both steps.

![](./static/provision-infra-dynamically-with-terraform-01.png)
