# Step 2: Add the Helm Chart and Delegate

You can add a Harness Delegate inline when you configure the first setting that needs it. For example, when we add a Helm chart, we will add a Harness Connector to the HTTP server hosting the chart. This Connector uses a Delegate to verify credentials and pull charts, so we'll install the Delegate, too.

1. In **Manifests**, click **Add Manifest**. The manifest types appear.

   ![](./static/helm-cd-quickstart-03.png)
2. Click **Helm Chart**, and then click **Continue**.
3. In **Specify Helm Chart Store**, click **HTTP Helm**.
   We're going to be pulling a Helm chart for NGINX from the Bitnami repo at `https://charts.bitnami.com/bitnami`. You don't need any credentials for pulling the public chart.
4. Click **New HTTP Helm Repo Connector**.
5. In the **HTTP Helm Repo Connector**, in **Name**, enter **helm-chart-repo**, and click **Continue**.
6. In **Helm Repository URL**, enter `https://charts.bitnami.com/bitnami`.
7. In **Authentication**, select **Anonymous**.
8.  Click **Continue**.
    Now we'll install and register a new Harness Delegate in your target cluster.
9.  In **Delegates Setup**, click **Install new Delegate**.
    The Delegate wizard appears.

    [![](./static/helm-cd-quickstart-04.png)](./static/helm-cd-quickstart-04.png)
10. Click **Kubernetes**, and then click **Continue**.

    ![](./static/helm-cd-quickstart-06.png)
11. Enter a name for the Delegate, like **quickstart**, click the **Small** size.
12. Click **Continue**.
13. Click **Download Script**. The YAML file for the Kubernetes Delegate will download to your computer as an archive.

   Open a terminal and navigate to where the Delegate file is located.

   You will connect to your cluster using the terminal so you can simply run the YAML file on the cluster.

   In the same terminal, log into your Kubernetes cluster. In most platforms, you select the cluster, click **Connect**, and copy the access command.

   Next, install the Harness Delegate using the **harness-delegate.yaml** file you just downloaded. In the terminal connected to your cluster, run this command:

   ```
   kubectl apply -f harness-delegate.yaml
   ```
   You can find this command in the Delegate wizard:

   ![](./static/helm-cd-quickstart-07.png)

   The successful output is something like this:

   ```
   % kubectl apply -f harness-delegate.yaml  
   namespace/harness-delegate unchanged  
   clusterrolebinding.rbac.authorization.k8s.io/harness-delegate-cluster-admin unchanged  
   secret/k8s-quickstart-proxy unchanged  
   statefulset.apps/k8s-quickstart-sngxpn created  
   service/delegate-service unchanged
   ```
1. In Harness, click **Verify**. It will take a few minutes to verify the Delegate. Once it is verified, close the wizard.
   Back in **Set Up Delegates**, you can select the new Delegate.
   In the list of Delegates, you can see your new Delegate and its tags.
2. Select the **Connect using Delegates with the following Tags** option.
3. Enter the tag of the new Delegate and click **Save and Continue**.
   When you are done, the Connector is tested. If it fails, your Delegate might not be able to connect to `https://charts.bitnami.com/bitnami`. Review its network connectivity and ensure it can connect.
4. Click **Continue**.
5. In **Manifest Details**, enter the following settings can click **Submit**.
   * **Manifest Identifier**: enter **nginx**.
   * **Helm Chart Name**: enter **nginx**.
   * **Helm Chart Version**: leave this empty.
   * **Helm Version**: select **Version 3**.

The Helm chart is added to the Service Definition.

![](./static/helm-cd-quickstart-08.png)

Next, we can target your Kubernetes cluster for deployment.
