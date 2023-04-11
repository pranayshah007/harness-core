 Credentials inherit_from_delegate
resource "harness_platform_connector_gcp" "test" {
  identifier  = "identifier"
  name        = "name"
  description = "test"
  tags        = ["foo:bar"]

  inherit_from_delegate {
    delegate_selectors = ["harness-delegate"]
  }
}
```
</details>

For the Terraform Provider service resource, go to [harness_platform_service](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_service).

```mdx-code-block
  </TabItem6>
  <TabItem6 value="Pipeline Studio" label="Pipeline Studio">
```

You connect to GCR using a Harness GCP Connector. For details on all the GCR requirements for the GCP Connector, see [Google Cloud Platform (GCP) Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/gcs-connector-settings-reference.md).

To add an artifact from GCR, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Artifacts**, select **Add Artifact Source**.
7. In **Select Artifact Repository Type**, click **GCR**, and then click **Continue**.
8. In **GCR Repository**, select or create a [Google Cloud Platform (GCP) Connector](../../../platform/7_Connectors/ref-cloud-providers/gcs-connector-settings-reference.md) that connects to the GCP account where the GCR registry is located.
9. Click **Continue**.
10. In **Artifact Source Name**, enter a name for the artifact.
11. In **GCR Registry URL**, select the GCR registry host name, for example `gcr.io`.
12. In **Image Path**, enter the name of the artifact you want to deploy.

    Images in repos need to reference a path starting with the project Id that the artifact is in, for example: `myproject-id/image-name`.
13. In **Tag**, enter or select the [Docker image tag](https://docs.docker.com/engine/reference/commandline/tag/) for the image or select a [runtime input or expression](../../../platform/20_References/runtime-inputs.md).
    
    ![](./static/kubernetes-services-10.png)
    
    If you use runtime input, when you deploy the pipeline, Harness will pull the list of tags from the repo and prompt you to select one.
14. Click **Submit**.
    
    The Artifact is added to the **Service Definition**.

```mdx-code-block
  </TabItem6>
</Tabs6>
```
