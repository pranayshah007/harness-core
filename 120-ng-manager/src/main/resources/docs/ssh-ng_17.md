### Rolling

For a Rolling strategy, you specify how many instances you want to deploy per phase.

Let’s say you have 10 target hosts in the stage Infrastructure Definition and you want to have 3 instances per phase. In **Instances**, you would enter 3. As a result, when execution starts there will be 4 phases: 3, 3, 3, 1. The number of instances per phase can be provided as a count or a percentage.

This is an example of the Rolling strategy using 2 hosts with 50% Instances.

![](./static/ssh-ng-196.png)

This means, that Harness will roll out to 50% of target hosts first, and then the remaining 50% if the first 50% were successful.

Harness creates 2 Phases.

![](./static/ssh-ng-197.png)

You can add any Approval steps inside the Phase Group. See [Adding ServiceNow Approval Steps and Stages](../../cd-advanced/approvals/using-harness-approval-steps-in-cd-stages.md), [Adding Jira Approval Stages and Steps](../../../platform/9_Approvals/adding-jira-approval-stages.md), and [Adding ServiceNow Approval Steps and Stages](../../../platform/9_Approvals/service-now-approvals.md).

The Looping Strategy for the first Phase deploys to 50% of the hosts (partitions):

```yaml
repeat:  
  items: <+stage.output.hosts>  
  maxConcurrency: 1  
  partitionSize: 50  
  unit: Percentage
```

The Looping Strategy for the second Phase repeats the partition count:

```yaml
repeat:  
  items: <+repeat.partition>
```

The `<+repeat.partition>` expression resolves how many instances (`items`) to iterate over per one partition (phase).

Let’s say we have 10 hosts and 4 partitions organized as 3, 3, 3, 1. The first partition includes 3 hosts, the second and third each have 3, and the last one has 1 host.

So, partition1 = host1, host2, host3, partition2 = host4, host5, host6, partition3 = host7, host8, host9, and partition4 = host10.
