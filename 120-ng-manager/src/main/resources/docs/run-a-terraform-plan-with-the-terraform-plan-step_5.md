# Review: Terraform Plan and Apply Steps

Typically the Terraform Plan step is used with the Terraform Apply step.

First, you add the Terraform Plan step and define the Terraform script for it to use.

Next, you add the Terraform Apply step, select **Inherit from Plan** in **Configuration Type**, and reference the Terraform Plan step using the same **Provisioner Identifier.**

![](./static/run-a-terraform-plan-with-the-terraform-plan-step-09.png)

For steps on using the Terraform Apply step, see:

* [Provision Target Deployment Infra Dynamically with Terraform](../../cd-infrastructure/terraform-infra/provision-infra-dynamically-with-terraform.md)
* [Provision with the Terraform Apply Step](run-a-terraform-plan-with-the-terraform-apply-step.md)

You can also simply run the Terraform Plan without applying the plan using the Terraform Apply step.
