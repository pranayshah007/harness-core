# Add the artifact

1. Back in **Artifactory Repository**, click **Continue**.
2. Enter the following artifact settings and click **Submit**. The following image shows how the Artifactory settings correspond to **Artifact Details**.
   * **Repository:** `lambda`.
   * **Artifact Directory:** `serverless`.
   * **Artifact Details:** `Value`.
   * **Artifact Path:** `handler.zip`.
	When you click one of the settings, the Delegate fetches artifact metadata from Artifactory.
1. Click **Submit**.

	The artifact is now in the Service.

	![](./static/serverless-lambda-cd-quickstart-120.png)

1. Click **Continue** to view the **Infrastructure**.

Now that you have configured the Service, we can define the target for our deployment.​
