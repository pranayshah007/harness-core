### Deployment logs

Here's an example of the log from a successful deployment.

<details>
<summary>ASG Rolling Deploy log example</summary>

```json
Deploy

Starting Rolling Deployment
Creating new version for launchTemplate demo-asg2
Created new version for launchTemplate demo-asg2
Getting Asg demo-asg2
Creating Asg demo-asg2
Waiting for Asg demo-asg2 to reach steady state
Polling every 20 seconds
Getting Asg demo-asg2
Getting Asg demo-asg2
0/2 instances are healthy
Getting Asg demo-asg2
2/2 instances are healthy
Created Asg demo-asg2 successfully
Getting Asg demo-asg2
Modifying scaling policies of Asg demo-asg2
Getting ScalingPolicies for Asg demo-asg2
No policies found which are currently attached with autoscaling group: [demo-asg2] to detach
No scaling policy provided which is needed be attached to autoscaling group: demo-asg2
Modified scaling policies of Asg demo-asg2 successfully
Modifying scheduled actions of autoscaling group demo-asg2
Getting ScheduledUpdateGroupActions for Asg demo-asg2
No scheduled actions found which are currently attached with autoscaling group: [demo-asg2] to detach
Attached scheduled action with name: scheduledActionName1
Modified scheduled actions of autoscaling group demo-asg2 successfully
Starting Instance Refresh for Asg demo-asg2
Waiting for Instance Refresh for Asg demo-asg2 to reach steady state
Polling every 20 seconds
Percentage completed: 0
Waiting for remaining instances to be available. For example: i-03e47024515f6d45a has insufficient data to evaluate its health with Amazon EC2.
Percentage completed: 0
Waiting for remaining instances to be available. For example: i-03e47024515f6d45a has insufficient data to evaluate its health with Amazon EC2.
Percentage completed: 0
Waiting for remaining instances to be available. For example: i-03e47024515f6d45a has insufficient data to evaluate its health with Amazon EC2.
Percentage completed: 0
Waiting for remaining instances to be available. For example: i-03e47024515f6d45a has insufficient data to evaluate its health with Amazon EC2.
Percentage completed: 100
Instance Refresh for Asg demo-asg2 ended successfully
Rolling Deployment Finished Successfully
```

</details>


```mdx-code-block
  </TabItem4>
  <TabItem4 value="Rolling Rollback step" label="Rolling Rollback step">
```

If deployment failure occurs, the stage or step [failure strategy](../../../platform/Pipelines/8_Pipelines/../../8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md) is initiated. Typically, this runs the Rolling Rollback step in the **Rollback** section of **Execution**.

During rollback of the first deployment, Harness deletes the ASG.

During rollback of subsequent deployments, Harness reverts the ASG to the previous state of ASG before deployment.

Harness stores configurations of the ASG you are deploying twice: 
- First storage: Harness stores the ASG configuration at the start of deployment for rollback *during deployment*.
- Second storage: Harness stores the ASG configuration at the end of deployment for rollbacks *post deployment*.

```mdx-code-block
  </TabItem4>
</Tabs4>
```
