# Looping Strategy and target hosts

To run the Command step on all the target hosts, you must use the Repeat [Looping Strategy](../../../platform/8_Pipelines/looping-strategies-matrix-repeat-and-parallelism.md) and expression `<+stage.output.hosts>`:


```
repeat:  
  items: <+stage.output.hosts>
```

![](./static/download-and-copy-artifacts-using-the-command-step-16.png)

When you run the Pipeline, you will see the Command step run on each host. For example, here is an SSH deployment where download, copy artifact, and copy config Command steps are run using the Looping Strategy:

![](./static/download-and-copy-artifacts-using-the-command-step-17.png)

The suffix \`_N` is used to identify each host.
