# Harness ASG pipelines

Once you have a the service and environment created, you can create the pipeline.

:::note

You can create a service and environment when you are building the pipeline or separately in **Services** and **Environments**. In this topic, we walk through building these separately. For more information, go to [CD pipeline modeling overview](https://developer.harness.io/docs/continuous-delivery/onboard-cd/cd-concepts/cd-pipeline-modeling-overview).

:::

The pipeline models the release process using execution steps, triggers, and other settings. For more information, go to [CD pipeline modeling overview](https://developer.harness.io/docs/continuous-delivery/onboard-cd/cd-concepts/cd-pipeline-modeling-overview).


```mdx-code-block
import Tabs2 from '@theme/Tabs';
import TabItem2 from '@theme/TabItem';
```
```mdx-code-block
<Tabs2>
  <TabItem2 value="YAML" label="YAML" default>
```

Here's a pipeline with a service, environment, and ASG Rolling Deploy and ASG Rolling Rollback steps:

```yaml
pipeline:
  projectIdentifier: CD_Docs
  orgIdentifier: default
  tags: {}
  stages:
    - stage:
        name: DeployRolling
        identifier: Deploy
        description: ""
        type: Deployment
        spec:
          deploymentType: Asg
          service:
            serviceRef: svcasg
            serviceInputs:
              serviceDefinition:
                type: Asg
                spec:
                  artifacts:
                    primary:
                      primaryArtifactRef: <+input>
                      sources: <+input>
                  variables:
                    - name: desiredCapacity
                      type: String
                      value: <+input>
          environment:
            environmentRef: ASG
            deployToAll: false
            infrastructureDefinitions:
              - identifier: asginfra
          execution:
            steps:
              - step:
                  name: ASG Rolling Deploy
                  identifier: AsgRollingDeploy
                  type: AsgRollingDeploy
                  timeout: 10m
                  spec:
                    useAlreadyRunningInstances: false
                    skipMatching: true
            rollbackSteps:
              - step:
                  type: AsgRollingRollback
                  name: ASG Rolling Rollback
                  identifier: ASG_Rolling_Rollback
                  spec: {}
                  timeout: 10m
        tags: {}
        failureStrategies:
          - onFailure:
              errors:
                - AllErrors
              action:
                type: StageRollback
  identifier: ASGROLLING
  name: ASG-ROLLING
```


```mdx-code-block
  </TabItem2>
  <TabItem2 value="API" label="API">
```

Create a pipeline using the [Create Pipeline](https://apidocs.harness.io/tag/Pipeline/#operation/postPipelineV2) API.

```json
curl -i -X POST \
  'https://app.harness.io/gateway/pipeline/api/pipelines/v2?accountIdentifier=<account_Id>&orgIdentifier=<org_Id>&projectIdentifier=<project_Id>' \
  -H 'Content-Type: application/yaml' \
  -H 'x-api-key: <token>' \
  -d 'pipeline:
    name: ASG Rolling
    identifier: ASG_Rolling
    projectIdentifier: CD_Docs
    orgIdentifier: default
    tags: {}
    stages:
      - stage:
          name: ASG Rolling
          identifier: ASG_Rolling
          description: ""
          type: Deployment
          spec:
            deploymentType: Asg
            service:
              serviceRef: AMI_Example
            environment:
              environmentRef: ASG
              deployToAll: false
              infrastructureDefinitions:
                - identifier: ASG_Infra
            execution:
              steps:
                - step:
                    name: Asg Rolling Deploy
                    identifier: AsgRollingDeploy
                    type: AsgRollingDeploy
                    timeout: 10m
                    spec:
                      useAlreadyRunningInstances: false
              rollbackSteps:
                - step:
                    name: Asg Rolling Rollback
                    identifier: AsgRollingRollback
                    type: AsgRollingRollback
                    timeout: 10m
                    spec: {}
          tags: {}
          failureStrategies:
            - onFailure:
                errors:
                  - AllErrors
                action:
                  type: StageRollback'
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_pipeline](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_pipeline).

```json
resource "harness_platform_pipeline" "example" {
  identifier = "ASG_Rolling"
  org_id     = "default"
  project_id = "CD_Docs"
  name       = "ASG Rolling"
  yaml = <<-EOT
      pipeline:
        name: ASG Rolling
        identifier: ASG_Rolling
        projectIdentifier: CD_Docs
        orgIdentifier: default
        tags: {}
        stages:
          - stage:
              name: ASG Rolling
              identifier: ASG_Rolling
              description: ""
              type: Deployment
              spec:
                deploymentType: Asg
                service:
                  serviceRef: AMI_Example
                environment:
                  environmentRef: ASG
                  deployToAll: false
                  infrastructureDefinitions:
                    - identifier: ASG_Infra
                execution:
                  steps:
                    - step:
                        name: Asg Rolling Deploy
                        identifier: AsgRollingDeploy
                        type: AsgRollingDeploy
                        timeout: 10m
                        spec:
                          useAlreadyRunningInstances: false
                  rollbackSteps:
                    - step:
                        name: Asg Rolling Rollback
                        identifier: AsgRollingRollback
                        type: AsgRollingRollback
                        timeout: 10m
                        spec: {}
              tags: {}
              failureStrategies:
                - onFailure:
                    errors:
                      - AllErrors
                    action:
                      type: StageRollback
  EOT
}
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Harness Manager" label="Harness Manager">
```

To create an ASG pipeline, do the following:

1. In your project, in CD (Deployments), click **Pipelines**.
2. Select **Create a Pipeline**.
3. Enter a name for the pipeline and select **Start**.
4. Select **Add Stage**, and then select **Deploy**.
5. Enter a name for the stage.
6. In **Deployment Type**, select **AWS Auto Scaling Group**, and then select **Set Up Stage**.
7. In **Service**, select the service you created earlier, and then select **Continue**.
8. In **Environment**, select the environment you created earlier.
9. In **Specify Infrastructure**, select the infrastructure definition you created earlier, and then select **Continue**.
10. In **Execution Strategies**, select the execution strategy for your deployment, and then select **Use Strategy**.
    
    The steps for the deployment are generated automatically by Harness. You can add any additional steps you want by selecting **Add Step**.

    In **Rollback**, you can see the rollback steps.
11. Select **Save**. The pipeline is saved.

```mdx-code-block
  </TabItem2>
</Tabs2>
```
