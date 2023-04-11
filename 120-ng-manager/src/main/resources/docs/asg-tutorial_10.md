## Infrastructure definition

To define the target ASG region, you add an infrastructure definition to a Harness environment. The infrastructure definition uses a Harness AWS connector and a region setting to define the deployment target.

You can use the same AWS connector you used when adding the AMI artifact in the Harness service. Ensure the AWS IAM user in the AWS connector credentials meets the [AWS policy requirements](#aws-policy-requirements).

```mdx-code-block
import Tabs1 from '@theme/Tabs';
import TabItem1 from '@theme/TabItem';
```
```mdx-code-block
<Tabs1>
  <TabItem1 value="YAML" label="YAML" default>
```

Here's a YAML example of an ASG infrastructure definition.

```yaml
infrastructureDefinition:
  name: asg-infra
  identifier: asginfra
  description: ""
  tags: {}
  orgIdentifier: default
  projectIdentifier: DoNotDelete_IvanBalan
  environmentRef: ASG
  deploymentType: Asg
  type: Asg
  spec:
    connectorRef: AWS_ASG_CONNECTOR
    region: us-east-1
  allowSimultaneousDeployments: false
```

```mdx-code-block
  </TabItem1>
  <TabItem1 value="API" label="API">
```

Create an infrastructure definition using the [Create Infrastructure](https://apidocs.harness.io/tag/Infrastructures#operation/createInfrastructure) API.

```yaml
curl -i -X POST \
  'https://app.harness.io/gateway/ng/api/infrastructures?accountIdentifier=<account_Id>' \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: <token>' \
  -d '{
    "identifier": "asginfra",
    "orgIdentifier": "default",
    "projectIdentifier": "CD_Docs",
    "environmentRef": "ASG",
    "name": "asginfra",
    "description": "",
    "tags": {
      "property1": "1",
      "property2": "2"
    },
    "type": "Asg",
    "yaml": "infrastructureDefinition:\n  name: asginfra\n  identifier: asginfra\n  description: \"\"\n  tags: {}\n  orgIdentifier: default\n  projectIdentifier: CD_Docs\n  environmentRef: ASG\n  deploymentType: Asg\n  type: Asg\n  spec:\n    connectorRef: AWS_ASG\n    region: us-east-2\n  allowSimultaneousDeployments: false"
  }'
```

```mdx-code-block
  </TabItem1>
  <TabItem1 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_infrastructure](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_infrastructure).

Here's an example of harness_platform_infrastructure:

```json
resource "harness_platform_infrastructure" "example" {
  identifier      = "asginfra"
  name            = "asginfra"
  org_id          = "default"
  project_id      = "CD_Docs"
  env_id          = "ASG"
  type            = "Asg"
  deployment_type = "Asg"
  yaml            = <<-EOT
        infrastructureDefinition:
          name: asginfra
          identifier: asginfra
          description: ""
          tags: {}
          orgIdentifier: default
          projectIdentifier: CD_Docs
          environmentRef: ASG
          deploymentType: Asg
          type: Asg
          spec:
            connectorRef: AWS_ASG
            region: us-east-2
          allowSimultaneousDeployments: false
      EOT
}
```

```mdx-code-block
  </TabItem1>
  <TabItem1 value="Harness Manager" label="Harness Manager">
```

To create the ASG infrastructure definition in an environment, do the following:

1. In your project, in CD (Deployments), select **Environments**.
2. Select the environment where you want to add the infrastructure definition.
3. In the environment, select **Infrastructure Definitions**.
4. Select **Infrastructure Definition**.
5. In **Create New Infrastructure**, in **Name**, enter a name for the new infrastructure definition.
6. In **Deployment Type**, select **AWS Auto Scaling Group**.
7. In **AWS Details**, in Connector, select or create a Harness AWS connector that connects Harness with the account where you want the ASG deployed.
   
   You can use the same AWS connector you used when adding the AMI artifact in the Harness service. Ensure the AWS IAM user in the AWS connector credentials meets the [AWS policy requirements](#aws-policy-requirements).
8. In **Region**, select the AWS region where you want the ASG deployed.
9. Select **Save**.

The infrastructure definition is added.

```mdx-code-block
  </TabItem1>
</Tabs1>
```

