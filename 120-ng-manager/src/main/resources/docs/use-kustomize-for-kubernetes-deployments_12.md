## Adding Kustomize Patches

You cannot use Harness variables in the base manifest or kustomization.yaml. You can only use Harness variables in kustomize patches you add in **Kustomize Patches Manifest Details**.In the Stage's **Service**, in **Manifests**, click **Add Manifest**.

In **Specify Manifest Type**, select **Kustomize Patches**, and click **Continue**.

![](./static/use-kustomize-for-kubernetes-deployments-04.png)

In **Specify Kustomize Patches Store**, select your Git provider and Connector. See [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md).

The Git Connector should point to the Git account or repo where you Kustomize files are located. In **Kustomize Patches** you will specify the path to the actual patch files.

Click **Continue**.

In **Manifest Details**, enter the path to your patch file(s):
  * **Manifest Identifier:** enter a name that identifies the patch file(s). You don't have to add the actual filename.
  * **Git Fetch Type:** select whether to use the latest branch or a specific commit Id.
  * **Branch**/**Commit Id**: enter the branch or commit Id.
  * **File/Folder Path:** enter the path to the patch file(s) from the root of the repo. Click **Add File** to add each patch file. The files you add should be the same files listed in `patchesStrategicMerge` of the main kustomize file in your Service.

The order in which you add file paths for patches in **File/Folder Path** is the same order that Harness applies the patches during the kustomization build.Small patches that do one thing are recommended. For example, create one patch for increasing the deployment replica number and another patch for setting the memory limit.

Click **Submit**. The patch file(s) is added to **Manifests**.

When the main kustomization.yaml is deployed, the patch is rendered and its overrides are added to the deployment.yaml that is deployed.
