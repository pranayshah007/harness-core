# Enable dynamic provisioning

These steps assume you've created a Harness CD stage before. If Harness CD is new to you, see [Kubernetes CD Quickstart](../../onboard-cd/cd-quickstarts/kubernetes-cd-quickstart.md).

We'll start in the stage's **Infrastructure** section because the **Service** settings of the stage don't have specific settings for Terraform provisioning. The Service manifests and artifacts will be deployed to the infrastructure provisioned by Harness and Terraform.

1. In the CD stage, click **Infrastructure**. If you haven't already specified your Environment, and selected the Infrastructure Definition, do so.
   
   The type of Infrastructure Definition you select determines what Terraform outputs you'll need to map later.
2. In **Dynamic provisioning**, click **Provision your infrastructure dynamically during the execution of your pipeline**.

The default Terraform provisioning steps appear:

![](./static/provision-infra-dynamically-with-terraform-00.png)

Harness automatically adds the Terraform Plan, [Harness Approval](../../cd-advanced/approvals/using-harness-approval-steps-in-cd-stages.md), and Terraform Apply steps. You can change these steps, but plan, approve, and apply is the most common process. We use that process in our Terraform documentation.
