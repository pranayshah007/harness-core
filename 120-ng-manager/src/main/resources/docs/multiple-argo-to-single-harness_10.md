# Creating GitOps Applications with multiple projects

When you have multiple Argo CD projects mapped to your Harness Project, you can choose which Argo CD project to use when you create a new GitOps Application in your Harness Project.

1. Go to the Harness Project that is mapped to multiple Argo CD projects.
2. In **Deployments**, click **GitOps**, and then click **Applications**.
3. Click **New Application**.
4. Enter a name for the new Application.
5. In **GitOps Agent**, select the Agent where you set up the mappings. The **Project** setting appears.
   
   If the Agent has only 1 Argo CD project mapped, the **Project** setting is not shown.

6. In **Project**, select the Argo CD project with the application you want to import.
   
   ![](./static/multiple-argo-to-single-harness-79.png)

7. Select/create the Harness Service and Environment, and then click **Continue**.
8. Complete the regular steps for adding the resource.

When you are done, the Application will appear in the GitOps Applications list.
