# Creating GitOps Clusters with multiple projects

When you have multiple Argo CD projects mapped to your Harness Project, you can choose which Argo CD project to use when you create a new GitOps Cluster in your Harness Project.

By default, in the Argo CD console, when you create a cluster it is not associated with an Argo CD project. You can add the cluster using the `argocd cluster add` CLI and its `--project` option.

1. Go to the Harness Project that is mapped to multiple Argo CD projects.
2. In **Deployments**, click **GitOps**, and then click **Settings**.
3. Click **Clusters**.
4. Click **New Cluster**.
5. Enter a name for the Cluster.
6. In **GitOps Agent**, select the Agent where you set up the mappings. The **Project** setting appears.
   
   If the Agent has only 1 Argo CD project mapped, the **Project** setting is not shown.

7. In **Project**, select the Argo CD project with the cluster you want to import.
   
   ![](./static/multiple-argo-to-single-harness-77.png)

8. Click **Continue**.
9.  Complete the regular steps for adding the resource.

When you are done, the Cluster will appear in the GitOps Cluster list.
