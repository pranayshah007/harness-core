# Enable dynamic provisioning

These steps assume you've created a Harness CD stage before. If Harness CD is new to you, see [Kubernetes CD Quickstart](../../onboard-cd/cd-quickstarts/kubernetes-cd-quickstart.md).

We'll start in the stage's **Infrastructure** section because the **Service** settings of the stage don't have specific settings for CloudFormation provisioning. The Service manifests and artifacts will be deployed to the infrastructure defined in **Infrastructure**.

1. In the CD stage, click **Infrastructure**. If you haven't already specified your **Environment**, and selected the **Infrastructure Definition**, do so.
   
   If you want to map CloudFormation outputs to the **Infrastructure Definition**, the type of **Infrastructure Definition** you select determines what CloudFormation outputs you can map later.
2. In **Dynamic provisioning**, click **Provision your target infrastructure dynamically during the execution of your Pipeline**.

The default CloudFormation provisioning steps appear:

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-01.png)

Harness automatically adds the **Create Stack**, [Harness Approval](../../cd-advanced/approvals/using-harness-approval-steps-in-cd-stages.md), and **Delete Stack** steps in **Execution**, and the **Rollback Stack** step in **Rollback**. You can change these steps, but **Create Stack** is required to run your CloudFormation template.
