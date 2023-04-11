## Option: Nexus

:::note

Currently, this feature is behind the Feature Flag `NG_NEXUS_ARTIFACTORY`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

1. Select **Nexus**, and click **Continue**.

   ![](./static/add-artifacts-for-kubernetes-deployments-04.png)

1. Select a [Nexus Connector](../../../platform/8_Pipelines/w_pipeline-steps-reference/nexus-connector-settings-reference.md) or create a new one.

   ![](./static/add-artifacts-for-kubernetes-deployments-05.png)

2. Click **Continue**. The Artifact Details settings appear.

![](./static/add-artifacts-for-kubernetes-deployments-06.png)

Based on your server and network configuration, choose one of the following and fill in the details:

* **Repository URL** - You can choose this for custom infrastructure with a specific hostname and/or port.  
  Enter the URL you would use in the Docker login to fetch the artifact. This is the same as the domain name and port you use for `docker login hostname:port`.
  
  ![](./static/add-artifacts-for-kubernetes-deployments-07.png)
* **Repository Port** - You can choose this for standard Nexus 3 installation without any additional infrastructure.  
  Enter the port you use for `docker login hostname:port`.  
  The port you enter will be used along with the domain, username, and password provided in the Nexus Connector.
  
  ![](./static/add-artifacts-for-kubernetes-deployments-08.png)
  
*  In **Repository**, enter the name of the repository where the artifact is located.
  
  Harness supports only the Docker repository format as the Artifact source for the Nexus 3 Artifact registry. In **Artifact Path**, enter the name of the artifact you want to deploy. For example `nginx`, `private/nginx`, `public/org/nginx`.
  
*  In **Tag**, select the tag for the image/artifact.
  
  ![](./static/add-artifacts-for-kubernetes-deployments-09.png)
  
  For details on the settings, see [Nexus Connector Settings Reference](../../../platform/8_Pipelines/w_pipeline-steps-reference/nexus-connector-settings-reference.md).
  
  You can optionally add validations to Tags. To do this, click the settings icon and select the validation type.
  
  ![](./static/add-artifacts-for-kubernetes-deployments-10.png)

Click **Submit**.

The Artifact is added to the Service Definition.
