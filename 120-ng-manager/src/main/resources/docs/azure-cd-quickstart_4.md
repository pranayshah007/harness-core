# Step 2: Add the Manifest and Values YAML

Next, we can add a Kubernetes manifest for our deployment. We'll use [publicly-available manifests and a values file](https://github.com/wings-software/harness-docs/tree/main/default-k8s-manifests/Manifests/Files) available from Harness.

1. In **Service Definition**, in **Deployment Type**, click **Kubernetes**.
2. In **Manifests**, click **Add Manifest**.
3. Select **K8s Manifest**, and click **Continue**.
4. In **Select K8sManifest Store**, click **GitHub**, and then click **New GitHub Connector**.
5. The **Git Connector** settings appear. Enter the following settings.
  1. **Name:** enter a name for the Connector, like **Quickstart**.**URL Type:** select **Repository**.**Connection Type:** select **HTTP**.**Git Repository URL:** enter `https://github.com/wings-software/harness-docs.git`.
  2. **Username and Token:** enter the username and a Github Personal Access Token for your Github account. You'll have to create a Harness secret for the password.
  3. In **Personal Access Token**, click **Create or Select a Secret**.
  4. Click **New Secret Text**.
  5. In **Secret Name**, enter a name for the secret like **github-pat**.
  6. In **Secret Value**, paste in a GitHub Personal access token. When you're logged into GitHub, these are typically listed at <https://github.com/settings/tokens>. For steps on setting up a GitHub PAT, see [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) from GitHub.
  7. Ensure you PAT has the **repo** scope selected:
  
  ![](static/azure-repo.png)
  
1. Click **Continue**.
2. In **Connect to the provider**, select **Connect through a Harness Delegate**, and click **Continue**. 
  
  Now we'll add a Harness Delegate to your Environment.
  
  The Harness Delegate is a software service you install in your environment that connects to the Harness Manager and performs tasks using your container orchestration platforms, artifact repositories, monitoring systems, etc.
  
3. In **Delegates Setup**, click **Install new Delegate**.

  The Delegate wizard appears.

  ![](./static/azure-cd-quickstart-97.png)

1. Click **Kubernetes**, and then click **Continue**.

  ![](./static/azure-cd-quickstart-99.png)

1. Enter a name for the Delegate, like **quickstart**, click the **Small** size.
2. Click **Continue**.
3. Click **Download YAML file**. The YAML file for the Kubernetes Delegate will download to your computer.
4. Open a terminal and navigate to where the Delegate file is located. You will connect to your cluster using the terminal so you can simply run the YAML file on the cluster.
5. In the same terminal, log into your Kubernetes cluster. In most platforms, you select the cluster, click **Connect**, and copy the access command.
6. Next, install the Harness Delegate using the **harness-delegate.yaml** file you just downloaded. In the terminal connected to your cluster, run this command:

  ```
  kubectl apply -f harness-delegate.yml
  ```
  You can find this command in the Delegate wizard:

  ![](./static/azure-cd-quickstart-100.png)

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
2. Back in **Set Up Delegates**, you can select the new Delegate. In the list of Delegates, you can see your new Delegate and its tags.
3. Select the **Connect using Delegates with the following Tags** option.
4. Enter the tag of the new Delegate and click **Save and Continue**.
5. In **Connection Test**, you can see that the connection is successful. Click **Finish**.
6. Back in **Specify K8s Manifest Store**, click **Continue**.
7. In **Manifest Details**, enter the following settings, test the connection, and click **Submit**.

  We are going to provide connection and path information for a manifest located at `https://github.com/wings-software/harness-docs/tree/main/default-k8s-manifests/Manifests/Files/templates`.
   1. **Manifest Identifier:** enter **manifests**.
   2. **Git Fetch Type:** select **Latest from Branch**.
   3. **Branch:** enter **main**.
   4. **File/Folder path:**`default-k8s-manifests/Manifests/Files/templates` 
  
  This is the path from the repo root. The manifest is now listed.

  ![](./static/azure-cd-quickstart-101.png)

  Next, let's add the values.yaml file for the deployment.

  Harness supports Go templating with a Values YAML file by default so you can template your manifests. Also, you can use [Harness expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) in your values.yaml file. 

  We will use a [values.yaml file](https://github.com/wings-software/harness-docs/blob/main/default-k8s-manifests/Manifests/Files/ng_values_dockercfg.yaml) that uses the `<+artifact.image>` expression to reference the artifact you will add later in **Artifacts**.The values file looks like this:

    ```yaml
    name: harness-quickstart  
    replicas: 1  
      
    image: <+artifact.image>  
    dockercfg: <+artifact.imagePullSecret>  
      
    createNamespace: true  
    namespace: <+infra.namespace>  
      
    # Service Type allow you to specify what kind of service you want.  
    # Possible values for ServiceType are:  
    # ClusterIP | NodePort | LoadBalancer | ExternalName  
    serviceType: LoadBalancer  
      
    # A Service can map an incoming port to any targetPort.  
    # targetPort is where application is listening on inside the container.  
    servicePort: 80  
    serviceTargetPort: 80  
      
    # Specify all environment variables to be added to the container.  
    # The following two maps, config and secrets, are put into a ConfigMap  
    # and a Secret, respectively.  
    # Both are added to the container environment in podSpec as envFrom source.  
    env:  
      config:  
        key1: value1  
      secrets:  
        key2: value2
    ```

1. Click **Add Manifest**.
2. In **Specify Manifest Type**, select **Values YAML**, and click **Continue**.
3. In **Specify Values YAML Store**, select the same GitHub Connector you used for your manifests, and then click **Continue**.
   1. In **Manifest Details**, enter the following and click **Submit**.
   2. **Manifest Identifier:** `values`.
   3. **Git Fetch Type:** `Latest from Branch`.
   4. **Branch:** `main`.
   5. **File Path:** `default-k8s-manifests/Manifests/Files/ng_values_dockercfg.yaml`.

The values file is listed.

![](./static/azure-cd-quickstart-102.png)

Next, let's add your artifact from ACR.
