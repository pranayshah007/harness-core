# Run your Pipeline

Once you run your Pipeline you will see the step applied to multiple hosts.

For example, here is a Custom Deployment stage using a Deployment Template.

![](./static/run-a-script-on-multiple-target-instances-02.png)

The Fetch Instances step returned two instances and the Shell Script step was executed on both using the Loop Strategy.

Here is an SSH deployment example with a Command step that uses `<+stage.output.hosts>`:

![](./static/run-a-script-on-multiple-target-instances-03.png)

Once the Pipeline is run, you can see each of the Deploy step run on each of the two target hosts:

![](./static/run-a-script-on-multiple-target-instances-04.png)

You can also add a Shell Script step to echo `<+stage.output.hosts>` to see the target hosts. The expression will resolve to a list like this SSH example that deploys to AWS EC2 instances:


```
[ec2-54-201-142-249.us-west-2.compute.amazonaws.com, ec2-54-190-26-183.us-west-2.compute.amazonaws.com]
```
