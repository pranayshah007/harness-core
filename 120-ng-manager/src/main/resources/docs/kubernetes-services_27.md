## Amazon Elastic Container Registry (ECR)

<details>
<summary>Use ECR artifacts</summary>

You connect to ECR using a Harness AWS connector. For details on all the ECR requirements for the AWS connector, see [AWS Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md).

```mdx-code-block
import Tabs8 from '@theme/Tabs';
import TabItem8 from '@theme/TabItem';
```
```mdx-code-block
<Tabs8>
  <TabItem8 value="YAML" label="YAML" default>
```

This example uses a Harness delegate installed in AWS for credentials.

<details>
<summary>ECR connector YAML</summary>

```yaml
connector:
  name: ECR
  identifier: ECR
  orgIdentifier: default
  projectIdentifier: CD_Docs
  type: Aws
  spec:
    credential:
      type: ManualConfig
      spec:
        accessKey: xxxxx
        secretKeyRef: secretaccesskey
      region: us-east-1
    delegateSelectors:
      - doc-immut
    executeOnDelegate: true
```
</details>

<details>
<summary>Service using ECR artifact YAML</summary>

```yaml
service:
  name: ECR
  identifier: ECR
  tags: {}
  serviceDefinition:
    spec:
      manifests:
        - manifest:
            identifier: myapp
            type: K8sManifest
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /values.yaml
              valuesPaths:
                - /Templates
              skipResourceVersioning: false
              enableDeclarativeRollback: false
      artifacts:
        primary:
          primaryArtifactRef: <+input>
          sources:
            - spec:
                connectorRef: ECR
                imagePath: todolist-sample
                tag: "1.0"
                region: us-east-1
              identifier: myapp
              type: Ecr
    type: Kubernetes
```
</details>

```mdx-code-block
  </TabItem8>
  <TabItem8 value="API" label="API">
```

Create the ECR connector using the [Create a Connector](https://apidocs.harness.io/tag/Connectors#operation/createConnector) API.

<details>
<summary>ECR connector example</summary>

```curl
curl --location --request POST 'https://app.harness.io/gateway/ng/api/connectors?accountIdentifier=12345' \
--header 'Content-Type: text/yaml' \
--header 'x-api-key: pat.12345.6789' \
--data-raw 'connector:
  name: ECR
  identifier: ECR
  orgIdentifier: default
  projectIdentifier: CD_Docs
  type: Aws
  spec:
    credential:
      type: ManualConfig
      spec:
        accessKey: xxxxx
        secretKeyRef: secretaccesskey
      region: us-east-1
    delegateSelectors:
      - doc-immut
    executeOnDelegate: true'
```
</details>

Create a service with an artifact source that uses the connector using the [Create Services](https://apidocs.harness.io/tag/Services#operation/createServicesV2) API.


```mdx-code-block
  </TabItem8>
  <TabItem8 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider ECR connector resource, go to [harness_platform_connector_aws](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_connector_aws).

<details>
<summary>ECR connector example</summary>

```json