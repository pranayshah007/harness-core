# Define the ECS target Infrastructure for the deployment

In the **Environment** section of the stage you define the target ECS cluster for the deployment.

**Environments and Infrastructure Definitions:** A Harness Environment represents where you are deploying your application. Each Infrastructure Definition in an Environment defines the target VM/cluster/etc. where you plan to deploy your application. An Environment can contain multiple Infrastructure Definitions. When you select an Environment in a Pipeline, you can pick which Infrastructure Definition to use.

1. In **Specify Environment**, click **New Environment**.
2. In **New Environment**, enter the following:
	1. Name: **ECS Tutorial**.
	2. Environment Type: **Pre-Production**.
3. Click **Save**.
4. In **Specify Infrastructure**, click **New Infrastructure**.
5. In **Create New Infrastructure**, in **Name**, enter **ECS Tutorial**.
6. For **Cluster Details**, see the following sections.
