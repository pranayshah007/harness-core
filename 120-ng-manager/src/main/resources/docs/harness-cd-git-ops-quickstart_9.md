# Step 6: Using the Application

Back in your Harness Application, you can view details on sync status, desired and live states, and perform GitOps.

![](./static/harness-cd-git-ops-quickstart-22.png)

* **Git details:** you can see the Git commit used as the source for the sync. Clicking the commit Ids opens the commit in the source provider.
* **Deployment:** the deployments dashboard shows when the Application was deployed (synced).
* **Resource View:** displays the desired to target state mapping of each Kubernetes object. Click any item to see its logs and manifest.
  The **Resource View** lets you inspect the live target infrastructure by:
	+ **Name:** Kubernetes object names.
	+ **Kind:** Kubernetes object types.
	+ **Health Status:** the health status of the Application. For example, is it syncing correctly (Healthy)?
	+ **Sync Status:** sort Kubernetes objects by their sync status.
* **App Details:** displays the settings of the Application. You can edit the settings and apply your changes, including the **Sync Options & Policy**.
* **Sync Status:** displays all of the sync operations performed by the Application.
* **Manifest:** displays the manifest of the Application. This is not the same as the source manifest in the Git source repo.  
You can edit the manifest in **Manifest** and the changes are synced in the target infrastructure, but when you do a full sync from your source repo the state of the source repo overwrites any changes made in **Manifest**.
* **App Diff:** displays a diff against the live state in the cluster and desired state in Git. 

Here's an example:

  ![](./static/harness-cd-git-ops-quickstart-23.png)

You can also initiate a Sync or Refresh from the main GitOps page.

![](./static/harness-cd-git-ops-quickstart-24.png)

- **Refresh** will pull the latest commit from Git and display whether the current Sync State is **Synced** or **Out of Sync**. It does not sync with the live cluster state. You can Refresh, then use App Diff to view the diff between the desired Git state with the live cluster state.
- **Sync** will sync the desired Git state with the live cluster state.
