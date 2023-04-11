### Canary

For Canary strategies, Harness calculates phase instances based on the number of hosts and the number of requested instances per phase.

Let’s say you have 10 hosts and you add 2 phases with 50% and 100%. This means Harness deploys on 5 instances in the first phase and on the rest of the instances in the second phase.

Here is an example of the Canary strategy using 2 hosts and 2 phases. The first phase deploys to 50% and the second phase deploys to 100%.

![](./static/ssh-ng-198.png)

This means, that Harness will roll out to 50% of target hosts first, and then the remaining 50% if the first 50% were successful.

Harness creates 2 phases as step groups.

![](./static/ssh-ng-199.png)

You can add any Approval steps between the Step Groups. See [Adding ServiceNow Approval Steps and Stages](../../cd-advanced/approvals/using-harness-approval-steps-in-cd-stages.md), [Adding Jira Approval Stages and Steps](../../../platform/9_Approvals/adding-jira-approval-stages.md), and [Adding ServiceNow Approval Steps and Stages](../../../platform/9_Approvals/service-now-approvals.md).

The Looping Strategy for the first Phase selects 50% of the target hosts:

```yaml
repeat:  
  items: <+stage.output.hosts>  
  start: 0  
  end: 50  
  unit: Percentage
```

The Looping Strategy for the second Phase starts at the 50% from the first phase and continues to 100%:

```
repeat:  
  items: <+stage.output.hosts>  
  start: 50  
  end: 100  
  unit: Percentage
```
