# Review: What Gets Destroyed?

When you add Terraform Plan and Apply steps, you specify the Terraform script that Harness will use for provisioning. You add a **Provisioner Identifier** to each step to identify the provisioning.

![](./static/remove-provisioned-infra-with-terraform-destroy-00.png)

When you destroy the provisioned infrastructure, you specify the same **Provisioner Identifier** in the Terraform Destroy step. The Provisioner Identifier enables Harness to locate the same Terraform script used for provisioning.

![](./static/remove-provisioned-infra-with-terraform-destroy-01.png)
