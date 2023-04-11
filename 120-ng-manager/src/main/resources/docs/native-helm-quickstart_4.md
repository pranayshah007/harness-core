# Step 2: Add the Helm Chart and Delegate

You can add a Harness Delegate inline when you configure the first setting that needs it. For example, when we add a Helm chart, we will add a Harness Connector to the HTTP server hosting the chart. This Connector uses a Delegate to verify credentials and pull charts, so we'll install the Delegate, too.

1. In **Manifests**, click **Add Manifest**. The manifest types appear.
   
   ![](./static/native-helm-quickstart-145.png)

   You can select a Helm Values YAML file or a Helm chart. For this quickstart, we'll use a publicly available Helm chart.
   The process isn't very different between these options. For Values YAML, you simply provide the Git branch and path to the Values YAML file.
   Click **Helm Chart** and then click **Continue**.
2. In **Specify Helm Chart Store**, click **HTTP Helm**.
   We're going to be pulling a Helm chart for NGINX from the Bitnami repo at `https://charts.bitnami.com/bitnami`. You don't need any credentials for pulling the public chart.
3. Click **New HTTP Helm Repo Connector**.
4. In the **HTTP Helm Repo Connector**, in **Name**, enter **helm-chart-repo**, and click **Continue**.
5. In **Helm Repository URL**, enter `https://charts.bitnami.com/bitnami`.
6.  In **Authentication**, select **Anonymous**.
7.  Click **Continue**.
    Now we'll install and register a new Harness Delegate in your target cluster.
8.  In **Set Up Delegates**, click **Install new Delegate**.
    The Delegate wizard appears.

   ![](./static/native-helm-quickstart-146.png)

9.  Click **Kubernetes**, and then click **Continue**.

    ![](./static/native-helm-quickstart-147.png)

10. Enter a name for the Delegate, like **quickstart**, click the **Small** size.
11. Click **Continue**.
12. Click **Download YAML file**. The YAML file for the Kubernetes Delegate will download to your computer as an archive.
13. Open a terminal and navigate to where the Delegate file is located.
    You will connect to your cluster using the terminal so you can simply run the YAML file on the cluster.
14. In the same terminal, log into your Kubernetes cluster. In most platforms, you select the cluster, click **Connect**, and copy the access command.
15. Next, install the Harness Delegate using the **harness-delegate.yaml** file you just downloaded. In the terminal connected to your cluster, run this command:

```
kubectl apply -f harness-delegate.yaml
```

You can find this command in the Delegate wizard:

![](./static/native-helm-quickstart-149.png)

The successful output is something like this:

```
% kubectl apply -f harness-delegate.yaml  
namespace/harness-delegate unchanged  
clusterrolebinding.rbac.authorization.k8s.io/harness-delegate-cluster-admin unchanged  
secret/k8s-quickstart-proxy unchanged  
statefulset.apps/k8s-quickstart-sngxpn created  
service/delegate-service unchanged
```

1. In Harness, click **Verify**. It will take a few minutes to verify the Delegate. Once it is verified, close the wizard.
2. Back in **Set Up Delegates**, you can select the new Delegate.
   In the list of Delegates, you can see your new Delegate and its tags.
3. Select the **Connect using Delegates with the following Tags** option.
4. Enter the tag of the new Delegate and click **Save and Continue**.
   When you are done, the Connector is tested. If it fails, your Delegate might not be able to connect to `https://charts.bitnami.com/bitnami`. Review its network connectivity and ensure it can connect.
   If you are using Helm V2, you will need to install Helm v2 and Tiller on the Delegate pod. For steps on installing software on the Delegate, see [Build custom delegate images with third-party tools](/docs/platform/2_Delegates/install-delegates/build-custom-delegate-images-with-third-party-tools.md).
5. Click **Continue**.
6. In **Manifest Details**, enter the following settings can click **Submit**.
   * **Manifest Identifier**: enter **nginx**.
   * **Helm Chart Name**: enter **nginx**.
   * **Helm Chart Version**: enter **8.8.1**.
   * **Helm Version**: select **Version 3**.

The Helm chart is added to the Service Definition.

![](./static/native-helm-quickstart-151.png)

Next, we can target your Kubernetes cluster for deployment.
