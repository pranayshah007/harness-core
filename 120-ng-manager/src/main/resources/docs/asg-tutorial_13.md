## Rolling

The first ASG rolling deployment will deploy a new ASG with the number of desired instances you have defined in your ASG configuration file in the Harness service used in the pipeline. Any other configuration files in the service are also applied.

Subsequent deployments will deploy a new version of the same ASG with any changes you have made in the Harness service configuration files (launch template, ASG configuration, etc.). This is the same as using the **Edit** buttons in the AWS console and creating a new version of the ASG.

Here's a flowchart that explains how Harness performs rolling deployments:

<details>
<summary>Rolling deployments flowchart</summary>

![ASG rolling flowchart](static/ab01a5afe7406d7dad3496fbf0544cd304c512179589a24ae47eefa418fdc989.png)  


</details>

```mdx-code-block
import Tabs4 from '@theme/Tabs';
import TabItem4 from '@theme/TabItem';
```
```mdx-code-block
<Tabs4>
  <TabItem4 value="Rolling Deploy step" label="Rolling Deploy step">
```

The Rolling Deploy step has the following options:

- **Same as already running Instances**
  - Enable this setting to use the scaling settings on the last ASG version deployed.
- **Minimum Healthy Percentage (optional)**
  - The percentage of the desired capacity of the ASG that must pass the group's health checks before the refresh can continue. For more information about these health checks, go to [Health checks for Auto Scaling instances](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-health-checks.html) from AWS.
- **Instance Warmup (optional)**
  - Go to [Set the default instance warmup for an Auto Scaling group](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-default-instance-warmup.html?icmpid=docs_ec2as_help_panel) from AWS.
- **Skip Matching**
  - Choose whether AWS Auto Scaling skips replacing instances that match the desired configuration. If no desired configuration is specified, then it skips replacing instances that have the same launch template and instance types that the ASG was using before the instance refresh started. For more information, go to [Use an instance refresh with skip matching](https://docs.aws.amazon.com/autoscaling/ec2/userguide/asg-instance-refresh-skip-matching.html) from AWS.
 

<details>
<summary>YAML example</summary>

```yaml
          execution:
            steps:
              - step:
                  name: ASG Rolling Deploy
                  identifier: AsgRollingDeploy
                  type: AsgRollingDeploy
                  timeout: 10m
                  spec:
                    useAlreadyRunningInstances: false
                    skipMatching: true
```

</details>
