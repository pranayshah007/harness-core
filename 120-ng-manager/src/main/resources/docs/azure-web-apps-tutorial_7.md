## Add your Artifact

You will add the same Docker image or non-containerized artifact you use in your Azure Web App.

1. In the Harness Service **Artifacts**, click **Add Primary Artifact**.
2. In **Specify Artifact Repository Type**, select the artifact registry type.
3. Select or create a Connector to this registry.

  For details on setting up each registry, go to [Connect to an Artifact Repo](../../../platform/7_Connectors/connect-to-an-artifact-repo.md).

  Once you have an artifact Connector set up and selected, you can fill out the **Artifact Details** settings.

  Here are some common examples.

  | **ACR** | **Artifactory** | **Docker Registry** |
  | --- | --- | --- |
  | ![](static/acr.png) | ![](static/artifactory.png) | ![](static/docker.png) |

  The settings for the Harness Connector and Artifact Details are a combination of the container settings in your Azure Web App.

  For example, here are the Docker Hub settings in Harness and an Azure Web App:

  ![](./static/azure-web-apps-tutorial-159.png)

  The above example uses a [publicly available Docker image from Harness](https://hub.docker.com/r/harness/todolist-sample/tags?page=1&ordering=last_updated). 

  You might want to use that the first time you set up an Azure Web App deployment.
4. When are done, click **Submit**.
1. Click **Continue** to move onto **Infrastructure**.
