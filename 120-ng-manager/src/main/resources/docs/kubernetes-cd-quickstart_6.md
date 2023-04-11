# Step 3: Define Your Target Cluster

The target cluster is your own Kubernetes cluster, hosted in your cloud environment. This is where we will deploy the Docker image using the manifest you selected.

Harness connects to all of the common cloud platforms and provides a platform-agnostic Kubernetes cluster connection that can connect to Kubernetes anywhere.

1. In **Infrastructure Details**, in **Specify your environment**, click **New Environment**. Just like with a Service, you can create a new Environment or selecting an existing one. We'll create a new one.
2. In **New Environment**, enter a name, select **Pre-Production**, and click **Save**. The new Environment appears.
3. In **Infrastructure Definition**, click **Kubernetes**.
    :::note

    Let's take a moment and review Harness Environments and Infrastructure Definitions. Harness Environments represent your deployment targets logically (QA, Prod, etc). You can add the same Environment to as many stages as you need. Infrastructure Definitions represent your target infrastructure physically. They are the actual clusters, hosts, etc.  
    
    By separating Environments and Infrastructure Definitions, you can use the same Environment in multiple stages while changing the target infrastructure settings with each stage.

    :::
4. An **Infrastructure Definition** is where you specify the target for your deployment. In this case, your Kubernetes cluster and namespace.
5. In **Cluster Details**, in **Connector**, click **Select a connector**.
6. Click **New Connector**.
7. The Kubernetes Cluster Connector appears.

   ![](./static/kubernetes-cd-quickstart-88.png)
 
The Kubernetes Cluster Connector is covered in detail [here](../../../platform/7_Connectors/ref-cloud-providers/kubernetes-cluster-connector-settings-reference.md), but let's quickly walk through it.

Let's look at the steps:

1. In **Kubernetes Cluster Connector**, in **Name**, enter **Kubernetes Quickstart**, and click **Continue**.
2. In **Details**, select **Use the credentials of a specific Harness Delegate**. We will select the Delegate next.
   
   ![](./static/kubernetes-cd-quickstart-89.png)

3. Click **Continue**.
4. Select the Kubernetes Delegate you added earlier using its Tags, and then click **Save and Continue**.
   Harness verifies the Connector.
5. Click **Finish**.
6. Select the new Connector and then click **Apply Selected**.
7. Back in **Cluster Details**, in **Namespace**, enter the target namespace for the deployment. For example, **default**. You can use any namespace in your target cluster.
8.  In **Advanced**, in **Release name**, enter `quickstart`.
    For a release name, you can use a built-in or custom expression or any text you like.
    Harness requires a release name for tracking. It is applied to the pods as a label. The release name must be unique across the cluster.
9.  When you are done, the **Cluster Details** will look something like this:

    ![](./static/kubernetes-cd-quickstart-90.png)

    The target infrastructure is complete. Now we can add our stage steps.
10. Click **Next** to move onto **Execution**.
