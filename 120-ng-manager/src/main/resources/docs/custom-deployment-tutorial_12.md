# Add the Docker image to deploy

1. In **Artifacts**, click **Add Primary Artifact**.
2. In **Specify Artifact Repository Type**, click **Docker Registry**, and click **Continue**.
3. In **Docker Registry Repository**, click **New Docker Registry Repository**.
4. In **Overview**, enter the name **Docker Hub**, and click **Continue**.
5. In **Docker Registry URL**, enter the following:
	1. **URL** `https://registry.hub.docker.com/v2/`.
	2. In **Authentication**, select **Anonymous**, and click **Continue**.
  ![](./static/custom-deployment-tutorial-19.png)
6. In **Connect to the provider**, click **Connect through a Harness Delegate**, and then click **Continue**.
7. In **Delegates Setup**, click **Only use Delegates with all of the following tags**, and then enter the tag of the Delegate you set up earlier.
8. Click **Save and Continue**.
9. After the test, click **Continue**.
10. In **Artifact Details**, enter the following:
	1. **Image Path:** `library/nginx`.
	2. **Tag:** Change the setting to a **Fixed value**, and then select **perl**.
  ![](./static/custom-deployment-tutorial-20.png)![](./static/custom-deployment-tutorial-21.png)
11. Click **Submit**. The artifact is now added to the Service.
![](./static/custom-deployment-tutorial-22.png)
12. Click **Save**. The Service is now added to the stage.
  ![](./static/custom-deployment-tutorial-23.png)
1. Click **Continue** to set up the Environment for the stage.
