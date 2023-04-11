# Step 2: Add the Kustomization

Now we can connect Harness to the repo containing the kustomization. We'll use a publicly available [hellword kustomization](https://github.com/wings-software/harness-docs/tree/main/kustomize/helloWorld) cloned from Kustomize.

All connections and operations are performed by Harness Delegates. So we'll also add a Harness Delegate to your target cluster. You can add the Delegate separately or as part of adding the kustomization files.

1. In **Service Definition**, in **Deployment Type**, click **Kubernetes**.
2. In **Manifests**, click **Add Manifest**.
    :::note

    **What about Artifacts?** In this quickstart the kustomization uses a publicly-available NGINX Docker image from DockerHub, and the location of the image is hardcoded in the manifest. The **Artifacts** section is only used when the public artifact is not hardcoded in the manifest or the repo is private. In those cases, you add the image in **Artifacts** with a Connector for the repo and then reference the image in a Kustomize Patch file (`image: <+artifact.image>`).

    :::
3. In **Specify Manifest Type**, click **Kustomize**, and click **Continue**.

   ![](./static/kustomize-quickstart-68.png)

4. In **Specify Kustomize Type**, select **GitHub**.
5. Click **New GitHub Connector**.
6. The **Git Connector** settings appear. Enter the following settings.
   * **Name:** enter a name for the Connector.
   * **URL Type:** select **Repository**.
   * **Connection Type:** select **HTTP**.
   * **Git Account URL:** enter `https://github.com/wings-software/harness-docs.git`.
   * **Username and Token:** Enter the username and a Github Personal Access Token for your Github account. You'll have to create a Harness secret for the password.
     * In **Personal Access Token**, click **Create or Select a Secret**.
     * Click **New Secret Text**.
     * In **Secret Name**, enter a name for the secret like **github-pat**.
     * In **Secret Value**, paste in a GitHub Personal access token. When you're logged into GitHub, these are typically listed at <https://github.com/settings/tokens>. For steps on setting up a GitHub PAT, see [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) from GitHub.
     * Ensure you PAT has the **repo** scope selected:

       ![](static/repoScope.png)
7. Click **Continue**.
8. In **Connect to the provider**, select **Connect through a Harness Delegate**, and then click **Continue**.
   We don't use the **Connect through Harness Platform** option here simply because you'll need a Delegate later for the connection to your target Kubernetes cluster. Typically, the **Connect through Harness Platform** option is a very quick way to make connections without having to use Delegates.
9. In **Delegates Setup**, click **Install new Delegate**.
   The Delegate wizard appears.
   
   ![](./static/kustomize-quickstart-69.png)

10. Click **Kubernetes**, and then click **Continue**.

   ![](./static/kustomize-quickstart-71.png)

11. Enter a name for the Delegate, like **quickstart**, click the **Small** size.
12. Click **Continue**.
13. Click **Download Script**. The YAML file for the Kubernetes Delegate will download to your computer as an archive.
14. Open a terminal and navigate to where the Delegate file is located.
   You will connect to your cluster using the terminal so you can simply run the YAML file on the cluster.
15. In the same terminal, log into your Kubernetes cluster. In most platforms, you select the cluster, click **Connect**, and copy the access command.
16. Next, install the Harness Delegate using the **harness-delegate.yaml** file you just downloaded. In the terminal connected to your cluster, run this command:
    ```
    kubectl apply -f harness-delegate.yaml
    ```
    You can find this command in the Delegate wizard:

    ![](./static/kustomize-quickstart-72.png)

    The successful output is something like this:
    ```
    % kubectl apply -f harness-delegate.yaml  
    namespace/harness-delegate unchanged  
    clusterrolebinding.rbac.authorization.k8s.io/harness-delegate-cluster-admin unchanged  
    secret/k8s-quickstart-proxy unchanged  
    statefulset.apps/k8s-quickstart-sngxpn created  
    service/delegate-service unchanged
    ```
17. In Harness, click **Verify**. It will take a few minutes to verify the Delegate. Once it is verified, close the wizard.
18. Back in **Set Up Delegates**, you can select the new Delegate.
    In the list of Delegates, you can see your new Delegate and its tags.
19. Select the **Connect using Delegates with the following Tags** option.
20. Enter the tag of the new Delegate and click **Save and Continue**.
    When you are done, the Connector is tested.
21. Click **Continue**.
22. In **Manifest Details**, enter the following settings, test the connection, and click **Submit**.
    We are going to provide connection and path information for a kustomization located at `https://github.com/wings-software/harness-docs/blob/main/kustomize/helloWorld/kustomization.yaml`.
    * **Manifest Identifier:** enter **kustomize**.
    * **Git Fetch Type**: select **Latest from Branch**.
    * **Branch:** enter **main**.
    * **Kustomize Folder Path:** enter `kustomize/helloWorld`. This is the path from the repo root.
    The kustomization is now listed.

    ![](./static/kustomize-quickstart-73.png)

23. Click **Next** at the bottom of the **Service** tab.

Now that the kustomization is defined, you can define the target cluster for your deployment.
