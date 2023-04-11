# Harness ASG services

The Harness ASG service contains the following:

- Launch template.
- ASG configuration file.
- Scaling policies (optional).
- Scheduled update group action (optional).
- The AMI image to use for the ASG.

Harness supports standard ASG JSON formatted files. For more information, go to [Get started with Amazon EC2 Auto Scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/get-started-with-ec2-auto-scaling.html) from AWS.

You can use remote files stored in a Git repo or the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/).

:::note

You can create a service when you are building the pipeline or separately in **Services**. In this topic, we walk through building the service separately. For more information, go to [CD pipeline modeling overview](https://developer.harness.io/docs/continuous-delivery/onboard-cd/cd-concepts/cd-pipeline-modeling-overview).

:::

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
  <TabItem value="YAML" label="YAML" default>
```

Here's an example where the configuration files are stored in the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/).

```yaml
service:
  name: svc-asg
  identifier: svcasg
  serviceDefinition:
    type: Asg
    spec:
      manifests:
        - manifest:
            identifier: launchTemplate
            type: AsgLaunchTemplate
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /asg/launchtemplate
        - manifest:
            identifier: launchConfig
            type: AsgConfiguration
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /asg/launchconfig
        - manifest:
            identifier: scalePolicy
            type: AsgScalingPolicy
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /asg/scalingPolicy.json
        - manifest:
            identifier: scheduledUpdateGroupAction
            type: AsgScheduledUpdateGroupAction
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /asg/scheduledUpdateGroupAction.json
      artifacts:
        primary:
          primaryArtifactRef: <+input>
          sources:
            - identifier: AMI-ARTIFACT
              spec:
                connectorRef: AWS_ASG_CONNECTOR
                region: us-east-1
                filters:
                  - name: ami-name
                    value: Returns local IP address at port 80
                version: Returns local IP address at port 80
              type: AmazonMachineImage
  gitOpsEnabled: false
```

```mdx-code-block
  </TabItem>
  <TabItem value="API" label="API">
```
Create a service using the [Create Services](https://apidocs.harness.io/tag/Services#operation/createServicesV2) API.

```yaml
curl -i -X POST \
  'https://app.harness.io/gateway/ng/api/servicesV2/batch?accountIdentifier=<Harness account Id>' \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: <Harness API key>' \
  -d '[{
    "identifier": "svcasg",
    "orgIdentifier": "default",
    "projectIdentifier": "CD_Docs",
    "name": "svc-asg",
    "description": "string",
    "tags": {
      "property1": "string",
      "property2": "string"
    },
    "yaml": "service:\n  name: svc-asg\n  identifier: svcasg\n  serviceDefinition:\n    type: Asg\n    spec:\n      manifests:\n        - manifest:\n            identifier: launchTemplate\n            type: AsgLaunchTemplate\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /asg/launchtemplate\n        - manifest:\n            identifier: launchConfig\n            type: AsgConfiguration\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /asg/launchconfig\n        - manifest:\n            identifier: scalePolicy\n            type: AsgScalingPolicy\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /asg/scalingPolicy.json\n        - manifest:\n            identifier: scheduledUpdateGroupAction\n            type: AsgScheduledUpdateGroupAction\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /asg/scheduledUpdateGroupAction.json\n      artifacts:\n        primary:\n          primaryArtifactRef: <+input>\n          sources:\n            - identifier: AMI-ARTIFACT\n              spec:\n                connectorRef: AWS_ASG_CONNECTOR\n                region: us-east-1\n                filters:\n                  - name: ami-name\n                    value: Returns local IP address at port 80\n                version: Returns local IP address at port 80\n              type: AmazonMachineImage\n  gitOpsEnabled: false"
  }]'
```

```mdx-code-block
  </TabItem>
  <TabItem value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_service](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_service).


```yaml
resource "harness_platform_service" "example" {
  identifier  = "identifier"
  name        = "name"
  description = "test"
  org_id      = "org_id"
  project_id  = "project_id"

  ## SERVICE V2 UPDATE
  ## We now take in a YAML that can define the service definition for a given Service
  ## It isn't mandatory for Service creation 
  ## It is mandatory for Service use in a pipeline

  yaml = <<-EOT
                service:
                  name: svc-asg
                  identifier: svcasg
                  serviceDefinition:
                    type: Asg
                    spec:
                      manifests:
                        - manifest:
                            identifier: launchTemplate
                            type: AsgLaunchTemplate
                            spec:
                              store:
                                type: Harness
                                spec:
                                  files:
                                    - /asg/launchtemplate
                        - manifest:
                            identifier: launchConfig
                            type: AsgConfiguration
                            spec:
                              store:
                                type: Harness
                                spec:
                                  files:
                                    - /asg/launchconfig
                        - manifest:
                            identifier: scalePolicy
                            type: AsgScalingPolicy
                            spec:
                              store:
                                type: Harness
                                spec:
                                  files:
                                    - /asg/scalingPolicy.json
                        - manifest:
                            identifier: scheduledUpdateGroupAction
                            type: AsgScheduledUpdateGroupAction
                            spec:
                              store:
                                type: Harness
                                spec:
                                  files:
                                    - /asg/scheduledUpdateGroupAction.json
                      artifacts:
                        primary:
                          primaryArtifactRef: <+input>
                          sources:
                            - identifier: AMI-ARTIFACT
                              spec:
                                connectorRef: AWS_ASG_CONNECTOR
                                region: us-east-1
                                filters:
                                  - name: ami-name
                                    value: Returns local IP address at port 80
                                version: Returns local IP address at port 80
                              type: AmazonMachineImage
                  gitOpsEnabled: false
              EOT
}
```

```mdx-code-block
  </TabItem>  
  <TabItem value="Harness Manager" label="Harness Manager">
```

To configure a Harness ASG service in the Harness Manager, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **AWS Auto Scaling Group**.
6. In **AWS ASG Configurations**, enter the following configuration files. 
  Only **Launch Template** and **ASG Configuration** are required.
   1. [Launch template](https://docs.aws.amazon.com/autoscaling/ec2/userguide/create-launch-template.html). Enter a standard AWS JSON or YAML formatted launch template. 
   2. In **ASG Configuration**, add a [ASG configuration file](https://docs.aws.amazon.com/codedeploy/latest/userguide/tutorials-auto-scaling-group-create-auto-scaling-group.html#tutorials-auto-scaling-group-create-auto-scaling-group-cli).
   3. [Scaling policy](https://docs.aws.amazon.com/autoscaling/ec2/userguide/examples-scaling-policies.html). This is optional.
   
   This is the same as the **Automatic scaling** option in the ASG console setup:

   ![Automatic scaling option in the ASG console setup](static/ae598a44899643397e7ee9502a8fc6698bd8703bff7209fe7a4c95c4b82c3704.png)  

   4. [Scheduled update group action](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-scheduled-scaling.html). This is optional.
   
   This is the same as the scheduled action in the ASG console setup:
   
   ![Edit scheduled action](static/5cfdb08c486ac86332089f793e1dbc0b011e1730c595d18e937370f6ca1587fc.png)  
   
   Next, in **Artifacts**, you add the private AMI image to use for the ASG instances.
1. In **Artifacts**, select **Add Artifact Source**.
2. In **Specify Artifact Repository Type**, select **Amazon Machine Image**, and select **Continue**.
3. In **Amazon Machine Image Repository**, in **AWS Connector**, select or create a Harness AWS connector, and then select **Continue**. Review [AWS policy requirements](#aws-policy-requirements) to ensure that the AWS credentials you provide are adequate.
4. In **Artifact Details**, enter a name to identify this AMI artifact in Harness.
5. In **Region**, select the region where the ASG is located.
6.  In **AMI Tags**, add any AWS Tags that you are using to identify your AMI. For details on these key/value pairs, go to [Tagging Your Amazon EC2 Resources](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html) from AWS.
7.  In **AMI Filters**, add AMI filters to locate the AMI resource. These are key/value pairs that identify the AMI Id. This must be a private AMI.
8.  In **Version Details** and **Version**, specify the AMI version to use.
9.  Select **Submit**.
10. Select **Save** to save the service.
    
    The Harness ASG service is complete. You can now use it in your Harness ASG pipelines.

```mdx-code-block
  </TabItem>
</Tabs>
```
