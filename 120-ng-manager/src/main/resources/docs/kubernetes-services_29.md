 Credentials inherit_from_delegate
resource "harness_platform_connector_aws" "test" {
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
  </TabItem8>
  <TabItem8 value="Pipeline Studio" label="Pipeline Studio">
```

You connect to ECR using a Harness AWS Connector. For details on all the ECR requirements for the AWS Connector, see [AWS Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md).

To add an artifact from ECR, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Artifacts**, select **Add Artifact Source**.
7. In **Artifact Repository Type**, click **ECR**, and then select **Continue**.
8. In **ECR Repository**, select or create an [AWS connector](../../../platform/7_Connectors/add-aws-connector.md) that connects to the AWS account where the ECR registry is located.
9. Select **Continue**.
10. In **Artifact Details**, in **Region**, select the region where the artifact source is located.
11. In **Image Path**, enter the name of the artifact you want to deploy.
12. In **Tag**, enter or select the [Docker image tag](https://docs.docker.com/engine/reference/commandline/tag/) for the image.
    
    ![ECR artifact details](static/74fe6d9189f8f18b2e854598026ab1db27944dab47c3056f4ffaaab93582242a.png)
    
    If you use runtime input, when you deploy the pipeline, Harness will pull the list of tags from the repo and prompt you to select one.
13. Select **Submit**.
    
    The Artifact is added to the Service Definition.

    ![ECR artifact source in a service](static/769c54fe91e7497b4aef3733f128361457b933f1d0eccd0d9b3491f1da4ed0c7.png)


```mdx-code-block
  </TabItem8>
</Tabs8>
```
