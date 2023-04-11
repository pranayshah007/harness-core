# Add the remote script and Kubernetes manifests

You can use your Git repo for the remote script and manifests in Manifests and Harness will use them at runtime.

If you are adding the image location to Harness as an Artifact in the Service Definition, see [Add Container Images as Artifacts for Kubernetes Deployments](add-artifacts-for-kubernetes-deployments.md).

1. In your Harness Kubernetes Service, in **Manifests**, click **Add Manifest**.
2. In **Specify Manifest Type**, select **K8s Manifest**, and then click **Next**.
3. In **Specify K8s Manifest Store**, click **Custom Remote.**
    
    ![](./static/add-a-custom-remote-script-and-manifests-38.png)

1. Click **Continue**. The Manifest Details appear. Now you can add your script to pull the package containing your manifests and specify the folder path for the manifests.
    
    ![](./static/add-a-custom-remote-script-and-manifests-39.png)

1. Enter the name in **Manifest Name**.
1. In **Custom Remote Manifest Extraction Script**, enter the path to the Git repo where your remote manifest script is available. This script runs on the Harness Delegate selected for the deployment.
2. In **Extracted Manifest File Location**, enter the folder path for the manifests.
3. In **Define Delegate Selector**, Harness selects the best Delegate. See [Select Delegates with Delegate Selectors and Tags](../../../platform/2_Delegates/manage-delegates/select-delegates-with-selectors.md). Select a specific delegate from the list of tags available for delegates or leave this blank and allow Harness to select a delegate.
4. In **Values.yaml**, the field is populated with the folder path for the values.yaml.
5. Click **Submit**. The new manifest is created and added to **Manifests** in Harness.

![](./static/add-a-custom-remote-script-and-manifests-40.png)
