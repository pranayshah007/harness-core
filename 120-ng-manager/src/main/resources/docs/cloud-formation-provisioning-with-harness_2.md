# CloudFormation Target Infra Provisioning Summary

You set up a CloudFormation target infrastructure provisioning in the following order:

1. Select Dynamic Provisioning. In the Pipeline stage **Infrastructure**, you select the **Dynamic Provisioning** option and select **CloudFormation**.

   ![](./static/cloud-formation-provisioning-with-harness-04.png)
   
   Harness automatically adds the CloudFormation **Create Stack**, **Harness Approval**, and **Delete Stack** steps.
   
   ![](./static/cloud-formation-provisioning-with-harness-05.png)
   
   You can change these steps, but these steps perform the most common CloudFormation target deployment infrastructure process. We use that process in our CloudFormation documentation.
2. In the CloudFormation Create Stack step, you link Harness to the CloudFormation templates you want to use. You add the scripts by connecting to a Git repo where the scripts are kept and setting up and other common options.
3. Map outputs to the target Infrastructure. Harness needs a few outputs so that it can target the provisioned infrastructure, such as a cluster namespace. You simply map some outputs to the required Harness target infrastructure settings.
4. Deployment. The Pipeline provisions the infrastructure defined in its target Infrastructure, and then, in the stage Execution, Harness deploys to that provisioned infrastructure.
