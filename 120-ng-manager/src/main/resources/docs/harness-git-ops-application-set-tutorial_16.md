## Create the PR Pipeline

To create the Pipeline, we'll simply create a new Service that includes the manifest you want deployed and select the dev Environment you created earlier.

1. In your Harness Project, click **Pipelines**.
2. Click **Create a Pipeline**.
3. In **Create new Pipeline**, enter the name **PR Pipeline**, and then click **Start**.
4. Click **Add Stage**, and select **Deploy**.
   
   ![](./static/harness-git-ops-application-set-tutorial-50.png)

5. Enter the following and click **Set Up Stage**:
	1. **Stage Name:** enter **PR Example**.
	2. **Deployment Type:** select **Kubernetes**.
	3. Enable the **GitOps** option.
  
  ![](./static/harness-git-ops-application-set-tutorial-51.png)

   The stage is created and the Service settings appear.
