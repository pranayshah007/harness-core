# Creating GitOps Repositories with multiple projects

When you have multiple Argo CD projects mapped to your Harness Project, you can choose which Argo CD project to use when you create a new GitOps Repository in your Harness Project.

1. Go to the Harness Project that is mapped to multiple Argo CD projects.
2. In **Deployments**, click **GitOps**, and then click **Settings**.
3. Click **Repositories**.
4. Click **New Repository**.
5. Select a repo type.
6. Enter a name for the Repository.
7. In **GitOps Agent**, select the Agent where you set up the mappings. The **Project** setting appears.
   
   If the Agent has only 1 Argo CD project mapped, the **Project** setting is not shown.

8. In **Project**, select the Argo CD project with the repo you want to import.
   
   ![](./static/multiple-argo-to-single-harness-78.png)

9.  Enter the **Repository URL** and click **Continue**. You might have multiple repos in that Argo CD project. So the Repository URL is required.
10. Complete the regular steps for adding the resource.

When you are done, the Repository will appear in the GitOps Repositories list.
