## Blue Green

An ASG Blue Green deployment uses two steps:

1. **ASG Blue Green Deploy step:**
   1. You specify the production and stage listener ports and rules to use.
   2. The new ASG is deployed to the stage target group.
2. **ASG Blue Green Swap Services step:**
   1. Harness modifies the ELB prod listener to forward requests to target groups associated with each new ASG version. 
   2. Harness swaps all traffic from stage to production listeners and ports. 
   3. Harness modifies the ELB stage listener to forward requests to target groups associated with old ASG version. 

The first ASG deployed is given a suffix using the format `[app_name]__1`, like `asgdemo__1`. The second ASG deployed is given the suffix `[app_name]__2`. 

Every subsequent deployment will simply create new versions of these ASGs instead of creating new ASGs. So the third deployment will create a new *version* of ASG `[app_name]__1`, route prod traffic to it, and route stage traffic to ASG `[app_name]__2`.

<details>
<summary>Blue/Green with Traffic Shift Summary</summary>

In this strategy, you specify production and stage listener ports and rules to use in the ASG Blue Green Deploy step. Next, the ASG Swap Services step swaps all traffic from stage to production.

A Blue/Green deployment reliably deploys your ASGs by maintaining new and old versions of ASGs. The ASGs run behind an Application Load Balancer (ALB) using two listeners, stage and prod. These listeners forward respectively to two target groups, stage and prod, where the new and old ASGs are run.

In the first stage of deployment, the new ASG is attached to the stage target group:

![first stage](static/ea87f58fb9e638f26d1c0a7cefde20158f4ad3c88496b3de827121992dd0ba0a.png)  

Blue/Green deployments are achieved by swapping routes between the target groups—always attaching the new ASG first to the stage target group, and then to the prod target group:

![second stage](static/88aa5c64d8375bea18c47e77b218c94fae1d06e6652c984c912d795132e84e63.png)  

</details>

Here's a flowchart that explains how Harness performs Blue Green deployments:

<details>
<summary>Blue Green deployments flowchart</summary>

![blue green flowchart map](static/65c67ea9418a480ee1fc97fce06fe551ac3afea9fb6e5297a2d70fcb7711ee0c.png)  

</details>


<details>
<summary>AWS requirements</summary>

In addition to the requirements of the Harness ASG service and environment, an ASG Blue Green deployment requires you to set up the following resources up within AWS:

- A security group that allows inbound access to your Application Load Balancer's listener ports.
- A pair of target groups—typically staging (Stage) and production (Prod)—both with the instance target type.
- An Application Load Balancer (ALB), with listeners for both your target groups' ports.

</details>

Let's take a look at the first two deployments.

<details>
<summary>First Blue Green deployment</summary>

The first Blue Green deployment follows these steps:

1. ASG Blue Green Deploy step:
   1. Checks that listener ARN and listener rule ARN for prod are valid.
   2. Fetches target group ARNs for prod.
   3. Checks that listener ARN and listener rule ARN for stage are valid.
   4. Fetches target group ARNs for stage.
   5. Creates launch template and ASG using the format `[app_name]__1`, like `asgdemo__1`.
   6. Sets the tag `BG_VERSION=BLUE` on new ASG.
2. ASG Blue Green Swap step:
   1. Not used as there is only one ASG at this point.

</details>


<details>
<summary>Second Blue Green deployment</summary>

At the start, the second Blue Green deployment will only have one prod ASG (the one deployed in the first Blue Green deployment). 

Harness will create a new ASG with the suffix `__2` and swap prod traffic to it. 

The second Blue Green deployment follows these steps:

1. ASG Blue Green Deploy step:
   1. Checks that listener ARN and listener rule ARN for prod are valid.
   2. Fetches target group ARNs for prod.
   3. Checks that listener ARN and listener rule ARN for stage are valid.
   4. Fetches target group ARNs for stage.
   5. Creates launch template and new ASG using the format `[app_name]__2`, like `asgdemo__2`.
   6. Sets the tag `BG_VERSION=GREEN` on new ASG, for example, `asgdemo__2`.
2. ASG Blue Green Swap step swaps target groups and updates tags:
   1. Modifies the ELB prod listener to forward requests to target groups associated with new autoscaling group.
   2. Modifies the ELB stage listener to forward requests to target groups associated with the old autoscaling group.
   3. Updates tags of the autoscaling groups.

Now there are two ASGs being used: a prod and a stage ASG. Subsequent deployments will create a new *versions* of these ASGs and swap prod traffic to the new ASG version from the previous ASG.
</details>

Blue Green deployment steps:

```mdx-code-block
import Tabs6 from '@theme/Tabs';
import TabItem6 from '@theme/TabItem';
```
```mdx-code-block
<Tabs6>
  <TabItem6 value="ASG Blue Green Deploy step" label="ASG Blue Green Deploy step" default>
```

The ASG Blue Green Deploy step has the following settings:

- **Load Balancer:** select the load balancer to use.
- **Prod Listener:** select the listener to use for prod traffic.
- **Prod Listener Rule ARN (optional):** select the ARN for the prod listener rule.
- **Stage Listener:** select the listener to use for stage traffic.
- **Stage Listener Rule ARN (optional):** select the ARN for the stage listener rule.

Harness fetches these AWS settings using the Harness AWS connector you have set up in the **Infrastructure Definition** in the **Environment** section of the stage.

```mdx-code-block
  </TabItem6>
  <TabItem6 value="ASG Blue Green Swap Services step" label="ASG Blue Green Swap Services step">
```

The ASG Blue Green Swap Services step has the following settings:

- **Downsize old ASG:** select this option to downsize the ASG that Harness swapped the traffic *from*. 
  
  For example, if there was an existing ASG named `asgdemo__1` and you deployed a new version of the ASG named `asgdemo__2` and swapped traffic to it, selecting **Downsize old ASG** will downsize `asgdemo__1`.

```mdx-code-block
  </TabItem6>
  <TabItem6 value="ASG Blue Green Rollback step" label="ASG Blue Green Rollback step">
```

The ASG Blue Green Rollback rolls back the routes and ASG created/updated in the Blue Green deployment.

Harness stores configurations of the ASG you are deploying twice: 
- First storage: Harness stores the ASG configuration at the start of deployment for rollback *during deployment*.
- Second storage: Harness stores the ASG configuration at the end of deployment for rollbacks *post deployment*.


```mdx-code-block
  </TabItem6>
</Tabs6>
```
