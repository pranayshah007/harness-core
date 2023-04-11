# Deployment summary

Here's a high-level summary of the setup steps and how Harness deploys ASGs.

<details>
<summary>Harness setup summary</summary>

Here's a summary of how you set up ASG deployments in Harness:

1. Create the Harness ASG service.
   1. Add launch template.
   2. Add ASG configuration.
   3. Add scaling policies (optional).
   4. Add scheduled update group action (optional).
   5. Add the AMI image to use for the ASG as an artifact.
2. Create the Harness ASG environment.
   1. Connect Harness to the AWS region where you want to deploy.
3. Define the Harness ASG pipeline execution.
   1. Select a deployment strategy (rolling, canary, blue green) and Harness automatically creates the steps to deploy the new ASG.
4. Deploy the Harness pipeline.

</details>


<details>
<summary>Deployment summary</summary>

Here's a summary of how Harness deploys new ASG versions:

1. First deployment:
   1. Harness takes the launch template and ASG configuration files you provide and creates a new ASG and its instances in your AWS account and region.
2. Subsequent deployments:
   1. Harness creates a new version of the launch template.
   2. Harness uses the new version of the launch template to update the ASG. For example, if you increased the desired capacity (`desiredCapacity`) for the ASG in your ASG configuration file, Harness will create a new version of the ASG with the new desired capacity.
   3. Instance refresh is triggered (a rolling replacement of all or some instances in the ASG).

Notes:
- ASG creation differs for rolling and blue green deployments:
  - For rolling, Harness updates the *existing* ASG with the new configuration.
  - For blue green, Harness creates a *new* ASG with a new revision suffix, like `asg-demo__2`. Once there are two ASGs (`asg-demo__1` and `asg-demo__2`) Harness alternately updates these *existing* ASGs with the new configuration on each successive deployment. 
  
</details>
