# Step 3: Define Your Target Cluster

1. In **Infrastructure**, in **Environment**, click **New Environment**.
2. In **Name**, enter **quickstart**, and click **Save**.
3. In **Infrastructure Definition**, select the **Kubernetes**.
4. In **Cluster Details**, click **Select Connector**. We'll create a new Kubernetes Connector to your target platform. We'll use the same Delegate you installed earlier.
5. Click **New Connector**.
6. Enter a name for the Connector and click **Continue**.
7. In **Details**, select **Use the credentials of a specific Harness Delegate**, and then click **Continue**.
   ![](./static/helm-cd-quickstart-09.png)
8. In **Set Up Delegates**, select the Delegate you added earlier by entering one of its Tags.
9.  Click **Save and Continue**. The Connector is tested. Click **Finish**.
10. Select the new Connector and click **Apply Selector**.
11. In **Namespace**, enter **default** or the namespace you want to use in the target cluster.
12. In **Release Name**, enter **quickstart**.
13. Click **Next**. The deployment strategy options appear.
