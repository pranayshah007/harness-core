# View the Harness Delegate selected for the deployment

The custom script runs on the Harness Delegate selected for deployment. If you selected a Delegate in the Kubernetes Cluster Cloud Provider used by the Workflow's Infrastructure Definition, then the script is run on that Delegate.

Harness creates a temporary working directory on the Delegate host for the downloaded package. You can reference the working directory in your script with `WORKING_DIRECTORY=$(pwd)` or `cd $(pwd)/some/other/directory`.

After deploying your Workflow, you can view the Delegate that was selected for the deployment.

Click on **Execution Summary**, and then click on **Custom Manifest Values Fetch Task** in the console to view the selected Delegate.

![](./static/add-a-custom-remote-script-and-manifests-41.png)
