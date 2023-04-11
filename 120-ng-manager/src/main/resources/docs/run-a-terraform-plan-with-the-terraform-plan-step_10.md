## Provisioner Identifier

Enter a unique value in **Provisioner Identifier**.

The Provisioner Identifier identifies the provisioning done in this step. You use the Provisioner Identifier in additional steps to refer to the provisioning done in this step.

The most common use of Provisioner Identifier is between the Terraform Plan and Terraform Apply steps. For the Terraform Apply step, to apply the provisioning from the Terraform Plan step, you use the same Provisioner Identifier.

![](./static/run-a-terraform-plan-with-the-terraform-plan-step-10.png)

You also use the same Provisioner Identifier with the [Terraform Destroy](remove-provisioned-infra-with-terraform-destroy.md) step to remove the provisioned resources.

![](./static/run-a-terraform-plan-with-the-terraform-plan-step-11.png)

You also use the same Provisioner Identifier with the [Terraform Rollback](rollback-provisioned-infra-with-the-terraform-rollback-step.md) step to rollback the provisioned resources.
