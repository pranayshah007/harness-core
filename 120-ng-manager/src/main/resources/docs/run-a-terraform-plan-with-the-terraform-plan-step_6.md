# Step 1: Add the Terraform Plan Step

In the Terraform Plan step, you connect Harness to your repo and add Terraform scripts.

You can add the Terraform Plan step in the stage's Infrastructure in **Dynamic Provisioning**. Dynamic Provisioning is used to provision the target infrastructure for the stage's deployment. Consequently, if you add Terraform Plan there, you must use a Terraform Apply step to apply the plan.

See [Provision Target Deployment Infra Dynamically with Terraform](../../cd-infrastructure/terraform-infra/provision-infra-dynamically-with-terraform.md).

You can also add a Terraform Plan step anywhere in the **Execution** steps for the stage. In that case, you aren't required to use a Terraform Apply step.

The Terraform Plan step has the following settings.
