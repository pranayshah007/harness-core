## Canary

The ASG canary deployment uses two step groups:

1. Canary Deployment:
   1. **ASG Canary Deploy step:** deploys a new ASG version with the name of your ASG and the extension `__Canary`. The canary ASG version can use the `desiredCapacity` in your ASG configuration file or you can set it in the **Instances** setting in the ASG Canary Deploy step.
   2. **ASG Canary Delete step:** deletes the canary ASG.
2. Primary Deployment:
   1. **ASG Rolling Deploy step:**
      1. The first deployment will deploy a new ASG with the number of desired instances you have defined in your ASG configuration file in the Harness service used in the pipeline. Any other configuration files in the service are also applied.
      2. Subsequent deployments will deploy a new *version* of the same ASG with any changes you have made in the Harness service configuration files (launch template, ASG configuration, etc.). This is the same as using the **Edit** buttons in the AWS console and creating a new version of the ASG.

Here's what the two step groups look like:

![ASG canary step groups](static/22f0a4be013dcf977b67e4f645941ce03ea5f63e6d9225a28f5efa383b5b5bdc.png)  


```mdx-code-block
import Tabs5 from '@theme/Tabs';
import TabItem5 from '@theme/TabItem';
```
```mdx-code-block
<Tabs5>
  <TabItem5 value="ASG Canary Deploy step" label="ASG Canary Deploy step" default>
```

The ASG Canary Deploy step deploys a new ASG with the name of your ASG and the extension `__Canary`. 

In the ASG Canary Deploy step, in **Instances**, you can specify how many instances to use in this temporary ASG. 

The **Instances** replaces the `desiredCapacity` in your ASG configuration file.

<details>
<summary>ASG Canary Deploy step log example</summary>

```json
Getting Asg demo-asg2__Canary
Creating launchTemplate demo-asg2__Canary
Created launchTemplate demo-asg2__Canary successfully
Getting Asg demo-asg2__Canary
Creating Asg demo-asg2__Canary
Waiting for Asg demo-asg2__Canary to reach steady state
Polling every 20 seconds
Getting Asg demo-asg2__Canary
Getting Asg demo-asg2__Canary
0/1 instances are healthy
Getting Asg demo-asg2__Canary
1/1 instances are healthy
Created Asg demo-asg2__Canary successfully
Getting Asg demo-asg2__Canary
Deployment Finished Successfully
```

</details>


```mdx-code-block
  </TabItem5>
  <TabItem5 value="ASG Canary Delete step" label="ASG Canary Delete step">
```

The ASG Canary Delete step deletes the temporary ASG created by the ASG Canary Deploy step.

<details>
<summary>ASG Canary Delete step log example</summary>

```json
Getting Asg demo-asg2__Canary
Deleting Asg demo-asg2__Canary
Waiting for deletion of Asg demo-asg2__Canary to complete
Polling every 20 seconds
Checking if Asg `demo-asg2__Canary` is deleted
Getting Asg demo-asg2__Canary
Checking if Asg `demo-asg2__Canary` is deleted
Getting Asg demo-asg2__Canary
Checking if Asg `demo-asg2__Canary` is deleted
Getting Asg demo-asg2__Canary
Checking if Asg `demo-asg2__Canary` is deleted
Getting Asg demo-asg2__Canary
Deleted Asg demo-asg2__Canary successfully
Deletion Finished Successfully
```
</details>


```mdx-code-block
  </TabItem5>
  <TabItem5 value="ASG Rolling Deploy step" label="ASG Rolling Deploy step">
```

This is the standard Harness ASG Rolling Deploy step. For details, go to [Rolling](#rolling).


```mdx-code-block
  </TabItem5>
  <TabItem5 value="Rollback steps" label="Rollback steps">
```

If deployment failure occurs, the stage or step [failure strategy](../../../platform/Pipelines/8_Pipelines/../../8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md) is initiated. Typically, this runs the rollback steps in the **Rollback** section of **Execution**.

For ASG canary deployments there are two rollback steps:

- **ASG Canary Delete:** deletes the canary ASG (`[app_name]__canary`). 
- **ASG Rolling Rollback:** deletes the new state and returns state to the previous ASG version.

```mdx-code-block
  </TabItem5>
</Tabs5>
```

