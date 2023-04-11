# Step 1: Add the Terraform Apply Step

When you are provisioning the target infrastructure for a deployment, you add the Terraform Plan step in the stage's Infrastructure in **Dynamic Provisioning**. Dynamic Provisioning is used to provision the target infrastructure for the stage's deployment. Consequently, if you add Terraform Plan there, you must use a Terraform Apply step to apply the plan.

See [Provision Target Deployment Infra Dynamically with Terraform](../../cd-infrastructure/terraform-infra/provision-infra-dynamically-with-terraform.md).

You can also add a Terraform Apply step anywhere in the **Execution** steps for the stage. In that case, you can use a Terraform Plan step but it's not required.

When you use the Terraform Apply step without using the Terraform Plan step, you set up the Terraform Apply step to connect Harness to your repo and add Terraform scripts.

The Terraform Apply step has the following settings.
