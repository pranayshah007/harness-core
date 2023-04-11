## Install and register the Harness Delegate

The Harness Delegate is a software service you install in your environment. It connects to the Harness Manager and performs ECS tasks. You can install the Delegate anywhere that has connectivity to your AWS account, even locally on you computer.

If you're new to Harness, read [Harness Platform architecture](../../../getting-started/harness-platform-architecture.md) to learn about how Harness uses a Delegate to perform deployment tasks.

1. Follow the steps here to install a Harness Delegate:
	1. [Install a Docker Delegate](/docs/platform/2_Delegates/install-delegates/overview.md).
	2. [Install Harness Delegate on Kubernetes](/docs/platform/2_Delegates/install-delegates/overview.md).

When you are done setting up the Delegate and it has registered with Harness, you'll see the Delegate's tags on the Delegates list page:

![](./static/ecs-deployment-tutorial-38.png)

Take note of that tag name. You will use it in your Harness AWS Connector and ECS Rolling Deploy step later in this tutorial to ensure that Harness uses that Delegate when performing deployments.
