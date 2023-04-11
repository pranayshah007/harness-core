# Cloud Functions environments

The Cloud Function environment contains an Infrastructure Definition that identifies the GCP account, project, and region to use.

Here's an example of a Cloud Functions environment.

```mdx-code-block
import Tabs1 from '@theme/Tabs';
import TabItem1 from '@theme/TabItem';
```
```mdx-code-block
<Tabs1>
  <TabItem1 value="YAML" label="YAML" default>
```

```YAML
environment:
  name: GCF
  identifier: GCF
  description: "GCF environment"
  tags: {}
  type: PreProduction
  orgIdentifier: default
  projectIdentifier: serverlesstest
  variables: []

```

```mdx-code-block
  </TabItem1>
  <TabItem1 value="API" label="API">
```

Create an environment using the [Create Environments](https://apidocs.harness.io/tag/Environments#operation/createEnvironmentV2) API.

```json
curl -i -X POST \
  'https://app.harness.io/gateway/ng/api/environmentsV2?accountIdentifier=<account_id>' \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: <token>' \
  -d '{
    "orgIdentifier": "default",
    "projectIdentifier": "CD_Docs",
    "identifier": "GCF",
    "tags": {
      "property1": "",
      "property2": ""
    },
    "name": "GCF",
    "description": "",
    "color": "",
    "type": "PreProduction",
    "yaml": "environment:\n  name: GCF\n  identifier: GCF\n  description: \"dev google cloud environment\"\n  tags: {}\n  type: PreProduction\n  orgIdentifier: default\n  projectIdentifier: serverlesstest\n  variables: []"
  }'
```


```mdx-code-block
  </TabItem1>
  <TabItem1 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_environment](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment) and [harness_platform_environment_service_overrides](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment_service_overrides).

Here's an example of harness_platform_environment:

```json
resource "harness_platform_environment" "example" {
  identifier = "GCF"
  name       = "GCF"
  org_id     = "default"
  project_id = "myproject"
  tags       = ["foo:bar", "baz"]
  type       = "PreProduction"

  ## ENVIRONMENT V2 Update
  ## The YAML is needed if you want to define the Environment Variables and Overrides for the environment
  ## Not Mandatory for Environment Creation nor Pipeline Usage

  yaml = <<-EOT
               environment:
                  name: GCF
                  identifier: GCF
                  description: ""
                  tags: {}
                  type: Production
                  orgIdentifier: default
                  projectIdentifier: myproject
                  variables: []
      EOT
}
```

```mdx-code-block
  </TabItem1>
  <TabItem1 value="Harness Manager" label="Harness Manager">
```

To create an environment, do the following:

1. In your project, in CD (Deployments), select **Environments**.
2. Select **New Environment**.
3. Enter a name for the new environment.
4. In **Environment Type**, select **Production** or **Pre-Production**. The **Production** or **Pre-Production** settings can be used in Harness RBAC to restrict who can deploy to these environments.
5. Select **Save**. The new environment is created.

Pipelines require that an environment have an infrastructure definition. We'll cover that next.

```mdx-code-block
  </TabItem1>
</Tabs1>
```
