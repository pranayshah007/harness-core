# Step 3: Add the Artifact

Now you can add an artifact from your ACR repo. We'll create a Harness Azure Connector to connect Harness with your ACR repo.

1. In **Artifacts**, click **Add Primary** **Artifact**.
2. In **Artifact Repository Type**, click **ACR**, and then click **Continue**.
3. In **ACR Repository**, click **New Azure Connector**.
4. Enter a name for the Connector, such as **Azure Quickstart**, and click **Continue**.
5. In **Details**, click **Specify credentials here**.
6. Enter the credentials for the Azure App registration you want to use. Here's an example of how App registration settings map to the Connector's **Details**:

  ![](./static/azure-cd-quickstart-103.png)

   + **Azure ACR and AKS Permissions:** make sure the Service Principal or Managed Identity has the [required permissions](../../../platform/7_Connectors/add-a-microsoft-azure-connector.md):
     
     + **ACR:** the **Reader** role must be assigned.
     + **AKS:** the **Owner** role must be assigned.
     + For a custom role, see the permissions in [Add a Microsoft Azure Cloud Connector](../../../platform/7_Connectors/add-a-microsoft-azure-connector.md).
  
1. Click **Continue**.
2. In **Delegates Setup**, click **Only use Delegates with all of the following tags**, and then select the Delegate you added earlier.
3. Click **Save and Continue**.
4. The Connection Test is performed. Once it's completed, you'll be back in **ACR Repository**. Click **Continue**.
5. In **Artifact Details**, select the Subscription Id where the artifact source is located.
6. In **Registry**, select the ACR registry to use.
7. In **Repository**, select the repo to use.
8. In **Tag**, enter or select the tag for the image.

  Here's an example of how ACR settings map to **Artifact Details**:

  ![](./static/azure-cd-quickstart-104.png)

1. Click **Submit**. The Artifact is added to the Service Definition.

  ![](./static/azure-cd-quickstart-105.png)

  Now that the artifact and manifest are defined, you can define the target cluster for your deployment.

1. Click **Next** at the bottom of the **Service** tab.
