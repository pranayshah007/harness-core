# Review: Terraform Plan and Apply Steps

Typically the Terraform Plan step is used with the Terraform Apply step.

First, you add the Terraform Plan step and define the Terraform script for it to use.

Next, you add the Terraform Apply step, select **Inherit from Plan** in **Configuration Type**, and reference the Terraform Plan step using the same **Provisioner Identifier.**

![](./static/run-a-terraform-plan-with-the-terraform-apply-step-02.png)

For steps on using the Terraform Plan with the Apply step, see:

* [Provision Target Deployment Infra Dynamically with Terraform](../../cd-infrastructure/terraform-infra/provision-infra-dynamically-with-terraform.md)

You can also simply run the Terraform Apply step without applying the plan from the Terraform Plan step.

In that case, you simply add the Terraform Apply step and select the **Inline** option in **Configuration Type**. Next, you add the Terraform script and settings in Terraform Apply. This scenario is described below.
