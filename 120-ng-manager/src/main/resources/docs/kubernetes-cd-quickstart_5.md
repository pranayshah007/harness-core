# Step 2: Add the Manifest

Next, we can add a Kubernetes manifest for NGINX. We'll use the [publicly-available manifest](https://github.com/kubernetes/website/blob/master/content/en/examples/application/nginx-app.yaml) available from Kubernetes.

1. In **Service Definition**, in **Deployment Type**, click **Kubernetes**.
2. In **Manifests**, click **Add Manifest**.
   :::note

   **What about Artifacts?** In this quickstart we are using a publicly-available NGINX Docker image from DockerHub, and the location of the image is hardcoded in the public manifest from Kubernetes. The **Artifacts** section is only used when the public artifact is not hardcoded in the manifest or the repo is private. In those cases, you add the image in **Artifacts** with a Connector for the repo and then reference the image in your values.yaml (`image: <+artifact.image>`). See [Add Container Images as Artifacts for Kubernetes Deployments](../../cd-advanced/cd-kubernetes-category/add-artifacts-for-kubernetes-deployments.md).

   :::
1. Select **K8s Manifest**, and click **Continue**.
2. In **Select K8sManifest Store**, click **GitHub**, and then click **New GitHub Connector**.
3. The **Git Connector** settings appear. Enter the following settings.
   * **Name:** enter a name for the Connector.
   * **URL Type:** select **Repository**.
   * **Connection Type:** select **HTTP**.
   * **Git Repository URL:** enter `https://github.com/kubernetes/website`.
   * **Username and Token:** Enter the username and a Github Personal Access Token for your Github account. You'll have to create a Harness secret for the password.
     1. In **Personal Access Token**, click **Create or Select a Secret**.
     2. Click **New Secret Text**.
     3. In **Secret Name**, enter a name for the secret like **github-pat**.
     4. In **Secret Value**, paste in a GitHub Personal access token.When you're logged into GitHub, these are typically listed at <https://github.com/settings/tokens>. For steps on setting up a GitHub PAT, see [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) from GitHub.Ensure you PAT has the **repo** scope selected:

     ![](static/repoScope.png)
4. Click **Continue**.
5. In **Connect to the provider**, select **Connect through a Harness Delegate**, and then click **Continue**.
   We don't use the **Connect through Harness Platform** option here simply because you'll need a Delegate later for the connection to your target Kubernetes cluster. Typically, the **Connect through Harness Platform** option is a very quick way to make connections without having to use Delegates.
6. In **Delegates Setup**, click **Install new Delegate**. The Delegate wizard appears.

   [![](./static/kubernetes-cd-quickstart-83.png)](./static/kubernetes-cd-quickstart-83.png)

7. Click **Kubernetes**, and then click **Continue**.

   ![](./static/kubernetes-cd-quickstart-85.png)

8. Enter a name for the Delegate, like **quickstart**, click the **Small** size.
9. Click **Continue**.
10. Click **Download YAML file**. The YAML file for the Kubernetes Delegate will download to your computer.
11. Open a terminal and navigate to where the Delegate file is located.
   You will connect to your cluster using the terminal so you can simply run the YAML file on the cluster.
12. In the same terminal, log into your Kubernetes cluster. In most platforms, you select the cluster, click **Connect**, and copy the access command.
    Next, install the Harness Delegate using the **harness-delegate.yaml** file you just downloaded.
13. In the terminal connected to your cluster, run this command:
   ```
   kubectl apply -f harness-delegate.yaml
   ```
   You can find this command in the Delegate wizard:

   ![](./static/kubernetes-cd-quickstart-86.png)

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
2. Back in **Set Up Delegates**, you can select the new Delegate.
   In the list of Delegates, you can see your new Delegate and its tags.
3. Select the **Connect using Delegates with the following Tags** option.
4. Enter the tag of the new Delegate and click **Save and Continue**.
   When you are done, the Connector is tested.
5. Click **Continue**.
6. In **Manifest Details**, enter the following settings, test the connection, and click **Submit**. We are going to provide connection and path information for a manifest located at `https://raw.githubusercontent.com/kubernetes/website/main/content/en/examples/application/nginx-app.yaml`.
   * **Manifest Identifier:** enter **nginx**.
   * **Git Fetch Type****:** select **Latest from Branch**.
   * **Branch:** enter **main**.
   * **File/Folder path:**`content/en/examples/application/nginx-app.yaml`. This is the path from the repo root.
    
   The manifest is now listed.

   ![](./static/kubernetes-cd-quickstart-87.png)

7. Click **Next** at the bottom of the **Service** tab.

Now that the artifact and manifest are defined, you can define the target cluster for your deployment.
