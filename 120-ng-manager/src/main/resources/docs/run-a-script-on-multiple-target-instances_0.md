---
title: Run a step on multiple target instances
description: This topic show you how to run the same step on multiple target hosts.
sidebar_position: 5
helpdocs_topic_id: c5mcm36cp8
helpdocs_category_id: y6gyszr0kl
helpdocs_is_private: false
helpdocs_is_published: true
---

When you are deploying to multiple hosts, such as with an SSH, WinRM, or Deployment Template stage, you can run the same step on all of the target hosts.

To run the step on all hosts, you use the Repeat [Looping Strategy](../../../platform/8_Pipelines/looping-strategies-matrix-repeat-and-parallelism.md) and identify all the hosts for the stage as the target:


```yaml
repeat:  
  items: <+stage.output.hosts>
```

Here's an example with a Shell Script step:

![](./static/run-a-script-on-multiple-target-instances-00.png)
