# Harness ASG environments

The Harness ASG environment is where you specify production and non-production environment settings. 

You also add a infrastructure definition to define the region where you want to deploy the ASG.

:::note

You can create an environment when you are building the pipeline or separately in **Environments**. In this topic, we walk through building the environment separately. For more information, go to [CD pipeline modeling overview](https://developer.harness.io/docs/continuous-delivery/onboard-cd/cd-concepts/cd-pipeline-modeling-overview).

:::

```mdx-code-block
import Tabs3 from '@theme/Tabs';
import TabItem3 from '@theme/TabItem';
```
```mdx-code-block
<Tabs3>
  <TabItem3 value="YAML" label="YAML" default>
```
Here's a YAML example of an ASG environment.

```yaml
environment:
  name: ASG
  identifier: ASG
  description: ""
  tags: {}
  type: Production
  orgIdentifier: default
  projectIdentifier: myProject
  variables: []
```

```mdx-code-block
  </TabItem3>
  <TabItem3 value="API" label="API">
```

Create an environment using the [Create Environments](https://apidocs.harness.io/tag/Environments#operation/createEnvironmentV2) API.

```yaml
curl -i -X POST \
  'https://app.harness.io/gateway/ng/api/environmentsV2?accountIdentifier=<account_id>' \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: <token>' \
  -d '{
    "orgIdentifier": "default",
    "projectIdentifier": "CD_Docs",
    "identifier": "ASG",
    "tags": {
      "property1": "",
      "property2": ""
    },
    "name": "ASG",
    "description": "",
    "color": "",
    "type": "PreProduction",
    "yaml": "environment:\n  name: ASG\n  identifier: ASG\n  tags: {}\n  type: PreProduction\n  orgIdentifier: default\n  projectIdentifier: CD_Docs\n  variables: []"
  }'
```

```mdx-code-block
  </TabItem3>
  <TabItem3 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_environment](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment) and [harness_platform_environment_service_overrides](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment_service_overrides).

Here's an example of harness_platform_environment: 

```yaml
resource "harness_platform_environment" "example" {
  identifier = "ASG"
  name       = "ASG"
  org_id     = "default"
  project_id = "myproject"
  tags       = ["foo:bar", "baz"]
  type       = "PreProduction"

  ## ENVIRONMENT V2 Update
  ## The YAML is needed if you want to define the Environment Variables and Overrides for the environment
  ## Not Mandatory for Environment Creation nor Pipeline Usage

  yaml = <<-EOT
               environment:
                  name: ASG
                  identifier: ASG
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
  </TabItem3>
  <TabItem3 value="Harness Manager" label="Harness Manager">
```

To create an environment, do the following:

1. In your project, in CD (Deployments), select **Environments**.
2. Select **New Environment**.
3. Enter a name for the new environment.
4. In **Environment Type**, select **Production** or **Pre-Production**.
   The **Production** or **Pre-Production** settings can be used in [Harness RBAC](https://developer.harness.io/docs/platform/role-based-access-control/rbac-in-harness/) to restrict who can deploy to these environments.
5. Select **Save**. The new environment is created.

Pipelines require that an environment have an infrastructure definition. We'll cover that next.

```mdx-code-block
  </TabItem3>
</Tabs3>
```
