# Terraform Target Infra Provisioning Summary

You set up a Terraform target infrastructure provisioning in the following order:

1. Select **Dynamic Provisioning**. In the Pipeline Infrastructure, you select the **Dynamic Provisioning** option and select **Terraform**. Harness automatically adds the Terraform Plan, [Harness Approval](../approvals/using-harness-approval-steps-in-cd-stages.md), and Terraform Apply steps. You can change these steps, but plan, approve, and apply is the most common process. We use that process in our Terraform documentation.
2. In the **Terraform Plan** step, you link Harness to the Terraform scripts you want to use. You add the scripts by connecting to a Git repo where the scripts are kept and setting up any inputs and other common options.
3. **Map outputs to the** **target Infrastructure**. Harness needs a few script outputs so that it can target the provisioned infrastructure, such as namespace. You simply map some script outputs to the required Harness target infrastructure settings.
4. **Deployment**. The Pipeline deploys to the provisioned infrastructure defined in its target Infrastructure Definition.

See [Provision Target Deployment Infra Dynamically with Terraform](../../cd-infrastructure/terraform-infra/provision-infra-dynamically-with-terraform.md).
