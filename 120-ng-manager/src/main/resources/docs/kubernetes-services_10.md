## OpenShift templates

<details>
<summary>Use OpenShift templates</summary>

Harness supports OpenShift for Kubernetes deployments.

For an overview of OpenShift support, see [Using OpenShift with Harness Kubernetes](../../cd-technical-reference/cd-k8s-ref/using-open-shift-with-harness-kubernetes.md).

```mdx-code-block
import Tabs3 from '@theme/Tabs';
import TabItem3 from '@theme/TabItem';
```

<Tabs3>
  <TabItem3 value="YAML" label="YAML" default>

Here's a YAML example for a service using an OpenShift template that is stored in the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/).

<details>
<summary>Example</summary>

```yaml
service:
  name: OpenShift Template
  identifier: OpenShift
  tags: {}
  serviceDefinition:
    spec:
      manifests:
        - manifest:
            identifier: nginx
            type: OpenshiftTemplate
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /OpenShift/templates/example-template.yml
              skipResourceVersioning: false
    type: Kubernetes
```
</details>

```mdx-code-block
  </TabItem3>
  <TabItem3 value="API" label="API">
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
    "yaml": "service:\n  name: OpenShift Template\n  identifier: OpenShift\n  tags: {}\n  serviceDefinition:\n    spec:\n      manifests:\n        - manifest:\n            identifier: nginx\n            type: OpenshiftTemplate\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /OpenShift/templates/example-template.yml\n              skipResourceVersioning: false\n    type: Kubernetes"
  }
]
```
</details>

```mdx-code-block
  </TabItem3>
  <TabItem3 value="Terraform Provider" label="Terraform Provider">
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
                name: OpenShift Template
                identifier: OpenShift
                tags: {}
                serviceDefinition:
                  spec:
                    manifests:
                      - manifest:
                          identifier: nginx
                          type: OpenshiftTemplate
                          spec:
                            store:
                              type: Harness
                              spec:
                                files:
                                  - /OpenShift/templates/example-template.yml
                            skipResourceVersioning: false
                  type: Kubernetes
              EOT
}
```
</details>

```mdx-code-block
  </TabItem3>
  <TabItem3 value="Pipeline Studio" label="Pipeline Studio">
```

To add an OpenShift Template to a service, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Manifests**, click **Add Manifest**.
7.  In **Specify Manifest Type**, select **OpenShift Template**, and then select **Continue.**
8.  In **Specify OpenShift Template Store**, select where your template is located. 
  
  You can use a Git provider, the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/), a custom repo, or Azure Repos.
1.  For example, click **GitHub**, and then select or create a new GitHub Connector. See [Connect to Code Repo](../../../platform/7_Connectors/connect-to-code-repo.md).
2.  Select **Continue**. **Manifest Details** appears.
3.  In **Manifest Identifier**, enter an Id for the manifest. It must be unique. It can be used in Harness expressions to reference this template's settings.
4.  In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit Id/Git Tag**, and then enter the branch or commit Id/[tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging) for the repo.
5.  In **Template** **File Path**, enter the path to the template file. The Connector you selected already has the repo name, so you simply need to add the path from the root of the repo to the file.
6.  Select **Submit**. The template is added to **Manifests**.

```mdx-code-block
  </TabItem3>
  <TabItem3 value="OpenShift Param" label="OpenShift Param" default>
```

OpenShift Param Files can be added in the following ways:

1. Attached to the OpenShift Template you added.
2. Added as a separate manifest.

![Params](static/9e15cbd984b566f357edc930d15ff7ce9d186c4d843615f5299710605926f811.png)

For an overview of OpenShift support, see [Using OpenShift with Harness Kubernetes](../../cd-technical-reference/cd-k8s-ref/using-open-shift-with-harness-kubernetes.md).

Let's look at an example where the OpenShift Param is attached to a template already added:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select the service with the OpenShift template.
3. Select **Configuration**.
4. In **Manifests**, select **Attach OpenShift Param**.
5. In **Enter File Path**, select where your params file is located.
6. Select **Submit**. The params file is added to **Manifests**.

You can now see the params file in the OpenShift Template **Manifest Details**.

![Manifest Details](static/bbeb75857343eb531ec2025d898bceb40393fa5068529e4ae69970ca3eaa8f4d.png)


```mdx-code-block
  </TabItem3>
</Tabs3>
```
