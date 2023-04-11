## Deployment Templates

For Deployment Templates, you can add a Command step to the template itself or anywhere in the Execution.

Since a Deployment Template can be used on any host type, the Command step can only be run on the Delegate. You must use the **Run on Delegate** option in the step.

To run the Command step on all the fetched hosts, you must put the Command step after the **Fetch Instances** step and use the Repeat Looping Strategy and expression `<+stage.output.hosts>`:

```yaml
repeat:  
  items: <+stage.output.hosts>
```

![](./static/download-and-copy-artifacts-using-the-command-step-06.png)

For the download artifact and copy artifact/config commands, you do not need the Looping Strategy. These commands should be run once on the Delegate. These commands will download the artifact and copy the artifact/config to the Delegate only, not the target hosts.

For a script command, you might want to run the script for each instance that was output from the Fetch instance step. In this case, using the Looping Strategy.
