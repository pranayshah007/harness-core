# Step 1: Add Manifests and Kustomization

Let's look at an example of connecting Harness to the repo containing the kustomization. We'll use a publicly available [helloword kustomization](https://github.com/wings-software/harness-docs/tree/main/kustomize/helloWorld) cloned from Kustomize.

All connections and operations are performed by Harness Delegates. You can add the Delegate separately or as part of adding the kustomization files.

In you Harness CD Pipeline, in a Deploy stage, click **Service**.

In **Service Definition**, in **Deployment Type**, click **Kubernetes**.

In **Manifests**, click **Add Manifest**.

**What about Artifacts?** In this example the kustomization uses a publicly-available NGINX Docker image from DockerHub, and the location of the image is hardcoded in the manifest. The **Artifacts** section is only used when the public artifact is not hardcoded in the manifest or the repo is private. In those cases, you add the image in **Artifacts** with a Connector for the repo and then reference the image in a Kustomize Patch file (`image: <+artifact.image>`). See [Option: Kustomize Patches](#option-kustomize-patches) below.

In **Specify Manifest Type**, click **Kustomize**, and click **Continue**.

![](./static/use-kustomize-for-kubernetes-deployments-01.png)

In **Specify Kustomize Store**, select your Git provider, such as **GitHub**.

If you already have a Git Connector that points to your Kustomization files, then select that. If not, click **New GitHub Connector**.

The **Git Connector** settings appear. Enter the settings described in [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md).

Click **Continue**.

In **Manifest Details**, enter the following settings, test the connection, and click **Submit**. We are going to provide connection and path information for a kustomization located at `https://github.com/wings-software/harness-docs/blob/main/kustomize/helloWorld/kustomization.yaml`.
  * **Manifest Identifier:** enter **kustomize**.
  * **Git Fetch Type****:** select **Latest from Branch**.
  * **Branch:** enter **main**.
  * **Kustomize Folder Path:**`kustomize/helloWorld`. This is the path from the repo root.

The **Kustomize Plugin Path** is described below in [Option: Use Plugins in Deployments](#option-use-plugins-in-deployments). The kustomization is now listed.

![](./static/use-kustomize-for-kubernetes-deployments-02.png)

Click **Next** at the bottom of the **Service** tab.

Now that the kustomization is defined, you can define the target cluster for your deployment.
