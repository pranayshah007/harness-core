# Add Kubernetes Manifests and Values YAML to File Store

You can create folders in File Store, add manifests to those folder, and Harness will use them at runtime. 

Let’s create a new folder called CD\_Manifests for three manifests (deployment.yaml, service.yaml, and namespace.yaml) and a folder named Templates for the values.yaml file. In the blank file that appears for each of these manifests in File Store, we can copy the contents of a sample manifest file into each of these blank files and save them in File Store.

Note: You can create a single folder and save all of the manifest files in that folder. Or you can store the values.yaml file in a folder that is separate from a Templates folder where the deployment.yaml, service.yaml, and namespace.yaml files are stored.

You can access sample manifest files at:

1. In Manifests, click **Add Manifest**.
2. In **Specify Manifest Type**, select **K8s Manifest** and then click **Continue**.
3. In the **Specify K8s Manifest Store**, select **Harness** and click **Continue**.

   ![](./static/add-inline-manifests-using-file-store-10.png)

1. In the **Manifest Details** dialog, enter a name for this manifest: CD\_Manifests.
4. In the field under **File Store**, click the down arrow.
5. In the **Create or Select an Existing Config File** dialog, select **Project**.
6. Click **New**, select **New Folder**, enter a name for the folder (for example, Templates), and click **Save**.
7. With this new folder selected, click **New File**.
8. Enter the file name deployment.yaml, select **Manifest for File Usage**, and click **Create**.
   
   ![](./static/add-inline-manifests-using-file-store-11.png)
1. When Harness displays the blank deployment.yaml manifest, copy the contents of the sample deployment.yaml manifest, and paste it in the blank file. Click **Save**.
9.  Create the namespace.yaml and service.yaml manifests by copying, pasting, and saving the manifests in File Store.
10. Click New, select **New Folder**, name the new folder (for example, Files) and click **Create**.
11. With the Templates folder selected, click **New** and select **New File**.
12. In **New File**, enter values.yaml as the manifest name, select **Manifest for File Usage**, and click **Create**.
13. When the blank values.yaml manifest appears, copy the contents of the values.yaml file, and paste it in the blank file. Click **Save**.

![](./static/add-inline-manifests-using-file-store-12.png)

You have now completed adding folders and manifests to File Store.
