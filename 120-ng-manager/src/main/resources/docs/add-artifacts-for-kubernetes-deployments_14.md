## Option: Docker Registry

1. Select **Docker Registry**, and click **Continue**.
2. The **Docker Registry** settings appear.
3. Select a [Docker Registry Connector](../../../platform/7_Connectors/ref-cloud-providers/docker-registry-connector-settings-reference.md) or create a new one.
4. In your Docker Registry Connector, to connect to a public Docker registry like Docker Hub, use `https://registry.hub.docker.com/v2/`. To connect to a private Docker registry, use `https://index.docker.io/v2/`.Click **Continue**.
5. In **Image path**, enter the name of the artifact you want to deploy, such as **library/nginx**.
6. In **Tag**, enter the [Docker image tag](https://docs.docker.com/engine/reference/commandline/tag/) for the image.
7. Click **Submit**.

The Artifact is added to the Service Definition.

![](./static/add-artifacts-for-kubernetes-deployments-03.png)
