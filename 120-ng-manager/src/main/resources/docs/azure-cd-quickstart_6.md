# Step 4: Define Your Target Cluster

The target cluster is your own AKS cluster, hosted in your Azure cloud. This is where we will deploy your ACR image using the manifest you selected.

1. In **Infrastructure Details**, in **Specify your environment**, click **New Environment**. Just like with a Service, you can create a new Environment or selecting an existing one. We'll create a new one.
2. In **New Environment**, enter a name, select **Pre-Production**, and click **Save**. The new Environment appears.
3. In **Infrastructure Definition**, click **Microsoft** **Azure**.

  ![](./static/azure-cd-quickstart-106.png)

  :::note

  Let's take a moment and review Harness Environments and Infrastructure Definitions. Harness Environments represent your deployment targets logically (QA, Prod, etc). You can add the same Environment to as many stages as you need. Infrastructure Definitions represent your target infrastructure physically. They are the actual clusters, hosts, etc.  
    
  By separating Environments and Infrastructure Definitions, you can use the same Environment in multiple stages while changing the target infrastructure settings with each stage.An **Infrastructure Definition** is where you specify the target for your deployment. In this case, your Kubernetes cluster and namespace.

  :::

1. In **Cluster details**, enter the following.
2. In **Connector**, click **Select a connector**.
3. Select the Azure Connector you added earlier, and then click **Apply Selected**.
4. In **Subscription Id**, select the Subscription where you AKS cluster is located.
5. In **Resource Group**, enter the resource group for your AKS cluster.
6. In **Cluster**, select the cluster name.
7. In **Namespace**, enter an existing namespace, such as **default**.

Now that the Stage's Infrastructure is complete, you can select the [deployment strategy](../../cd-deployments-category/deployment-concepts.md) for this stage of the Pipeline.
