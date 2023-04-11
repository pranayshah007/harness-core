# Create your pipeline

You can use the repeat looping strategy with `<+stage.output.hosts>` to target multiple hosts in the following deployment types:

* [SSH (Traditional)](../../onboard-cd/cd-quickstarts/ssh-ng.md)
* [WinRM](../../onboard-cd/cd-quickstarts/win-rm-tutorial.md)
* [Custom deployments using Deployment Templates](../../onboard-cd/cd-quickstarts/custom-deployment-tutorial.md)

All three types can deploy your artifacts to hosts located in Microsoft Azure, AWS, or any platform-agnostic Physical Data Center (PDC).

In the case of SSH and WinRM you can use the **Repeat** Looping Strategy with `<+stage.output.hosts>` on a step anywhere in Execution because the hosts are fetched as part of the Environment.

For **Deployment Templates**, any step using the Repeat Looping Strategy with `<+stage.output.hosts>` must come after the **Fetch Instances** step.
