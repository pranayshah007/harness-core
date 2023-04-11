## Adding a Cloud Function service

Here's how you add a Harness Cloud Function service.

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
  <TabItem value="YAML" label="YAML" default>
```

Here's a Cloud Functions service YAML example.

```yaml
service:
  name: helloworld
  identifier: Google_Function
  serviceDefinition:
    type: GoogleCloudFunctions
    spec:
      manifests:
        - manifest:
            identifier: GoogleFunction
            type: GoogleCloudFunctionDefinition
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /GoogleFunctionDefinition.yaml
      artifacts:
        primary:
          primaryArtifactRef: <+input>
          sources:
            - spec:
                connectorRef: gcp_connector
                project: cd-play
                bucket: cloud-functions-automation-bucket
                artifactPath: helloworld
              identifier: helloworld
              type: GoogleCloudStorage
```

```mdx-code-block
  </TabItem>
  <TabItem value="API" label="API">
```

Create a service using the [Create Services](https://apidocs.harness.io/tag/Services#operation/createServicesV2) API.

```json
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
    "yaml": "service:\n  name: helloworld\n  identifier: Google_Function\n  serviceDefinition:\n    type: GoogleCloudFunctions\n    spec:\n      manifests:\n        - manifest:\n            identifier: GoogleFunction\n            type: GoogleCloudFunctionDefinition\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /GoogleFunctionDefinition.yaml\n      artifacts:\n        primary:\n          primaryArtifactRef: <+input>\n          sources:\n            - spec:\n                connectorRef: gcp_connector\n                project: cd-play\n                bucket: cloud-functions-automation-bucket\n                artifactPath: helloworld\n              identifier: helloworld\n              type: GoogleCloudStorage"
  }]'
```

```mdx-code-block
  </TabItem>
  <TabItem value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_service](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_service).

```json
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
                  name: helloworld
                  identifier: Google_Function
                  serviceDefinition:
                    type: GoogleCloudFunctions
                    spec:
                      manifests:
                        - manifest:
                            identifier: GoogleFunction
                            type: GoogleCloudFunctionDefinition
                            spec:
                              store:
                                type: Harness
                                spec:
                                  files:
                                    - /GoogleFunctionDefinition.yaml
                      artifacts:
                        primary:
                          primaryArtifactRef: <+input>
                          sources:
                            - spec:
                                connectorRef: gcp_connector
                                project: cd-play
                                bucket: cloud-functions-automation-bucket
                                artifactPath: helloworld
                              identifier: helloworld
                              type: GoogleCloudStorage              
              EOT
}
```

```mdx-code-block
  </TabItem>
  <TabItem value="Harness Manager" label="Harness Manager">
```

To configure a Harness Cloud Function service in the Harness Manager, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Google Cloud Functions**.
6. In **Function Definition**, enter the manifest YAML. You have two options.
   - You can add the manifest YAML inline.
   - Select **Add Function Definition** and connect Harness with the Git repo where the manifest YAML is located. You can also use the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/).
7. In **Artifacts**, add the Google Cloud Storage location of the ZIP file that corresponds to the manifest YAML.
8. Select **Save**.

```mdx-code-block
  </TabItem>
</Tabs>
```
