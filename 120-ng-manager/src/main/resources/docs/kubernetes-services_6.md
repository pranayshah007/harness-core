## Helm Charts

<details>
<summary>Use Helm Charts</summary>

You can use Helm charts stored in an HTTP Helm Repository, OCI Registry, a Git repo provider, a cloud storage service (Google Cloud Storage, AWS S3, Azure Repo), a custom repo, or the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/).


```mdx-code-block
import Tabs1 from '@theme/Tabs';
import TabItem1 from '@theme/TabItem';
```

<Tabs1>
  <TabItem1 value="YAML" label="YAML" default>

Here's a YAML example for a service with manifests hosted in Github and the nginx image hosted in Docker Hub.

<details>
<summary>Example</summary>

```yaml
service:
  name: Helm Chart
  identifier: Helm_Chart
  tags: {}
  serviceDefinition:
    spec:
      manifests:
        - manifest:
            identifier: nginx
            type: HelmChart
            spec:
              store:
                type: Http
                spec:
                  connectorRef: Bitnami
              chartName: nginx
              helmVersion: V3
              skipResourceVersioning: false
              commandFlags:
                - commandType: Template
                  flag: mychart -x templates/deployment.yaml
    type: Kubernetes
```

</details>


```mdx-code-block
  </TabItem1>
  <TabItem1 value="API" label="API">
```

Create a service using the [Create Services](https://apidocs.harness.io/tag/Services#operation/createServicesV2) API.

<details>
<summary>Services API example</summary>

```json
[
  {
    "identifier": "KubernetesTest",
    "orgIdentifier": "default",
    "projectIdentifier": "CD_Docs",
    "name": "KubernetesTest",
    "description": "string",
    "tags": {
      "property1": "string",
      "property2": "string"
    },
    "yaml": "service:\n  name: Helm Chart\n  identifier: Helm_Chart\n  tags: {}\n  serviceDefinition:\n    spec:\n      manifests:\n        - manifest:\n            identifier: nginx\n            type: HelmChart\n            spec:\n              store:\n                type: Http\n                spec:\n                  connectorRef: Bitnami\n              chartName: nginx\n              helmVersion: V3\n              skipResourceVersioning: false\n              commandFlags:\n                - commandType: Template\n                  flag: mychart -x templates/deployment.yaml\n    type: Kubernetes"
  }
]
```
</details>

```mdx-code-block
  </TabItem1>
  <TabItem1 value="Terraform Provider" label="Terraform Provider">
```

For the Terraform Provider resource, go to [harness_platform_service](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_service).

<details>
<summary>Example</summary>

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
                  name: Helm Chart
                  identifier: Helm_Chart
                  tags: {}
                  serviceDefinition:
                    spec:
                      manifests:
                        - manifest:
                            identifier: nginx
                            type: HelmChart
                            spec:
                              store:
                                type: Http
                                spec:
                                  connectorRef: Bitnami
                              chartName: nginx
                              helmVersion: V3
                              skipResourceVersioning: false
                              commandFlags:
                                - commandType: Template
                                  flag: mychart -x templates/deployment.yaml
                    type: Kubernetes
              EOT
}
```
</details>

```mdx-code-block
  </TabItem1>
  <TabItem1 value="Pipeline Studio" label="Pipeline Studio">
```

To add a Helm chart to your service, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Manifests**, click **Add Manifest**.
7. In **Specify Manifest Type**, select **Helm Chart**, and click **Continue**.
8. In **Specify Helm Chart Store**, select the storage service you're using.
   
   ![helm storage](static/a49287968d8e99d3368420384bab12d62206d48fa714cd0c61eed12ca14c641f.png)  


   For the steps and settings of each option, go to [Connectors](https://developer.harness.io/docs/category/connectors) or [Connect to a Git repo](https://developer.harness.io/docs/platform/Connectors/connect-to-code-repo).
   
   Once your Helm chart is added, it appears in the **Manifests** section. For example:
   
   ![](./static/kubernetes-services-05.png)

```mdx-code-block
  </TabItem1>
</Tabs1>
```
