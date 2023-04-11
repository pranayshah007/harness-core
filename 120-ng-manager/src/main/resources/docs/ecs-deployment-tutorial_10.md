# Add the Docker image to deploy

1. In **Artifacts**, click **Add Primary Artifact**.
2. In **Specify Artifact Repository Type**, click **Docker Registry**, and click **Continue**.
3. In **Docker Registry Repository**, click **New Docker Registry Repository**.
4. In **Overview**, enter the name **Docker Hub**, and click **Continue**.
5. In **Docker Registry URL**, enter the following:
	1. **URL** `https://registry.hub.docker.com/v2/`.
	2. In **Authentication**, select **Anonymous**, and click **Continue**.
  
  ![](./static/ecs-deployment-tutorial-43.png)

6. In **Connect to the provider**, click **Connect through a Harness Delegate**, and then click **Continue**.
7. In **Delegates Setup**, click **Only use Delegates with all of the following tags**, and then enter the tag of the Delegate you set up earlier.
8. Click **Save and Continue**.
9. After the test, click **Finish**.
10. Back in **Docker Registry Repository**, click **Continue**.
11. In **Artifact Details**, for **Image path**, enter `library/nginx`.
12. For **Tag**, change the setting to a **Fixed value**.

    ![](./static/ecs-deployment-tutorial-44.png)
13. Select **perl**.

    ![](./static/ecs-deployment-tutorial-45.png)

1.  Click **Submit**. The artifact is now added to the Service.
2.  Click **Save**. The Service is now added to the stage.
  ![](./static/ecs-deployment-tutorial-46.png)
1. Click **Continue** to add the target ECS cluster.
