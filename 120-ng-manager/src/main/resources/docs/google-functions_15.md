## Cloud Functions infrastructure definitions

You will need a Harness GCP Connector with [correct permissions](#cloud-functions-permission-requirements) to deploy Cloud Functions in GCP.

You can pick the same GCP connector you used in the Harness service to connect to Google Cloud Storage for the artifact, or create a new connector. 


```mdx-code-block
import Tabs2 from '@theme/Tabs';
import TabItem2 from '@theme/TabItem';
```
```mdx-code-block
<Tabs2>
  <TabItem2 value="YAML" label="YAML" default>
```

Here's a YAML example of a Cloud Function infrastructure definition.

```yaml
infrastructureDefinition:
  name: dev
  identifier: dev
  description: "dev google cloud infrastructure"
  tags: {}
  orgIdentifier: default
  projectIdentifier: serverlesstest
  environmentRef: dev
  deploymentType: GoogleCloudFunctions
  type: GoogleCloudFunctions
  spec:
    connectorRef: gcp_connector
    project: cd-play
    region: us-central1
  allowSimultaneousDeployments: false
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="API" label="API">
```

Create an infrastructure definition using the [Create Infrastructure](https://apidocs.harness.io/tag/Infrastructures#operation/createInfrastructure) API.

```json
curl -i -X POST \
  'https://app.harness.io/gateway/ng/api/infrastructures?accountIdentifier=<account_Id>' \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: <token>' \
  -d '{
    "identifier": "dev",
    "orgIdentifier": "default",
    "projectIdentifier": "serverlesstest",
    "environmentRef": "dev",
    "name": "dev",
    "description": "",
    "tags": {
      "property1": "1",
      "property2": "2"
    },
    "type": "Asg",
    "yaml": "infrastructureDefinition:\n  name: dev\n  identifier: dev\n  description: \"dev google cloud infrastructure\"\n  tags: {}\n  orgIdentifier: default\n  projectIdentifier: serverlesstest\n  environmentRef: dev\n  deploymentType: GoogleCloudFunctions\n  type: GoogleCloudFunctions\n  spec:\n    connectorRef: gcp_connector\n    project: cd-play\n    region: us-central1\n  allowSimultaneousDeployments: false"
  }'
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_infrastructure](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_infrastructure).

Here's an example of harness_platform_infrastructure:

```json
resource "harness_platform_infrastructure" "example" {
  identifier      = "dev"
  name            = "dev"
  org_id          = "default"
  project_id      = "serverlesstest"
  env_id          = "dev"
  type            = "GoogleCloudFunctions"
  deployment_type = "GoogleCloudFunctions"
  yaml            = <<-EOT
        infrastructureDefinition:
          name: dev
          identifier: dev
          description: "dev google cloud infrastructure"
          tags: {}
          orgIdentifier: default
          projectIdentifier: serverlesstest
          environmentRef: dev
          deploymentType: GoogleCloudFunctions
          type: GoogleCloudFunctions
          spec:
            connectorRef: gcp_connector
            project: cd-play
            region: us-central1
          allowSimultaneousDeployments: false
      EOT
}
```


```mdx-code-block
  </TabItem2>
  <TabItem2 value="Harness Manager" label="Harness Manager">
```

To create the ASG infrastructure definition in an environment, do the following:

1. In your project, in CD (Deployments), select **Environments**.
2. Select the environment where you want to add the infrastructure definition.
3. In the environment, select **Infrastructure Definitions**.
4. Select **Infrastructure Definition**.
5. In **Create New Infrastructure**, in **Name**, enter a name for the new infrastructure definition.
6. In **Deployment Type**, select **Google Cloud Functions**.
7. In **Google Cloud Provider Details**, in **Connector**, select or create a Harness GCP connector that connects Harness with the account where you want the Cloud Function deployed.
   
   You can use the same GCP connector you used when adding the Cloud Function artifact in the Harness service. Ensure the GCP credentials in the GCP connector credentials meets the [requirements](#cloud-functions-permission-requirements).
8. In **Project**, select the GCP project where you want to deploy.
9. In **Region**, select the GCP region where you want to deploy.
10. Select **Save**.

The infrastructure definition is added.


```mdx-code-block
  </TabItem2>
</Tabs2>
```
