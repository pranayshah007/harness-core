## Option: Artifactory

![](./static/add-artifacts-for-kubernetes-deployments-11.png)

Select an [Artifactory Connector](../../../platform/7_Connectors/ref-cloud-providers/artifactory-connector-settings-reference.md) or create a new one.

![](./static/add-artifacts-for-kubernetes-deployments-12.png)

Click **Continue**. The **Artifact Details** settings appear.

In **Repository URL**, enter the URL you would use in the Docker login to fetch the artifact. This is the same as the domain name and port you use for `docker login hostname:port`. For more information, see [Artifactory Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/artifactory-connector-settings-reference.md).

In **Repository**, enter the name of the repository where the artifact is located.

In **Artifact Path**, enter the name of the artifact you want to deploy. For example `nginx`, `private/nginx`, `public/org/nginx`.

In **Tag**, select the tag for the image/artifact. For more information, see [Artifactory Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/artifactory-connector-settings-reference.md).

![](./static/add-artifacts-for-kubernetes-deployments-13.png)

Click **Submit**.

The Artifact is added to the Service Definition.

You can add sidecar artifacts the same way.

When you run the Pipeline, select the build of the artifact(s) to use.

![](./static/add-artifacts-for-kubernetes-deployments-14.png)
