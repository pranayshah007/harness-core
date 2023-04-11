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
  </TabItem7>
  <TabItem7 value="Pipeline Studio" label="Pipeline Studio">
```

You connect to Google Artifact Registry using a Harness GCP Connector. 

For details on all the Google Artifact Registry requirements for the GCP Connector, see [Google Cloud Platform (GCP) Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/gcs-connector-settings-reference.md).

To add an artifact from Google Artifact Registry, do the following:


1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Artifacts**, select **Add Artifact Source**.
7. In **Artifact Repository Type**, select **Google Artifact Registry**, and then select **Continue**.
8. In **GCP Connector**, select or create a [Google Cloud Platform (GCP) Connector](../../../platform/7_Connectors/ref-cloud-providers/gcs-connector-settings-reference.md) that connects to the GCP account where the Google Artifact Registry is located. 
9. Select **Continue**.
10. In **Artifact Details**, you are basically creating the pull command. For example:
    
    ```
    docker pull us-central1-docker.pkg.dev/docs-play/quickstart-docker-repo/quickstart-image:v1.0
    ```
12. In **Artifact Source Name**, enter a name for the artifact.
13. In **Repository Type**, select the format of the artifact.
14. In **Project**, enter the Id of the GCP project.
15. In **Region**, select the region where the repo is located.
16. In **Repository Name**, enter the name of the repo.
17. In **Package**, enter the artifact name.
18. In **Version Details**, select **Value** or **Regex**.
19. In **Version**, enter or select the [Docker image tag](https://docs.docker.com/engine/reference/commandline/tag/) for the image or select [runtime input or expression](../../../platform/20_References/runtime-inputs.md).
    
    ![](./static/kubernetes-services-11.png)
    If you use runtime input, when you deploy the pipeline, Harness will pull the list of tags from the repo and prompt you to select one.

    :::note
    
    If you used Fixed Value in **Version** and Harness is not able to fetch the image tags, ensure that the GCP service account key used in the GCP connector credentials, or in the service account used to install the Harness delegate, has the required permissions. See the **Permissions** tab in this documentation. 
    
    :::
20. Click **Submit**.
    The Artifact is added to the **Service Definition**.


```mdx-code-block
  </TabItem7>
</Tabs7>
```
