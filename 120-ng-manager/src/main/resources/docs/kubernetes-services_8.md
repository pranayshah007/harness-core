## Kustomize

<details>
<summary>Use Kustomize</summary>

Harness supports Kustomize deployments. You can use overlays, multibase, plugins, sealed secrets, patches, etc, just as you would in any native kustomization.

```mdx-code-block
import Tabs2 from '@theme/Tabs';
import TabItem2 from '@theme/TabItem';
```

<Tabs2>
  <TabItem2 value="YAML" label="YAML" default>

Here's a YAML example for a service using a publicly available [helloword kustomization](https://github.com/wings-software/harness-docs/tree/main/kustomize/helloWorld) cloned from Kustomize.

<details>
<summary>Example</summary>

```yaml
service:
  name: Kustomize
  identifier: Kustomize
  serviceDefinition:
    type: Kubernetes
    spec:
      manifests:
        - manifest:
            identifier: kustomize
            type: Kustomize
            spec:
              store:
                type: Github
                spec:
                  connectorRef: Kustomize
                  gitFetchType: Branch
                  folderPath: kustomize/helloworld
                  branch: main
              pluginPath: ""
              skipResourceVersioning: false
  gitOpsEnabled: false
```
</details>


```mdx-code-block
  </TabItem2>
  <TabItem2 value="API" label="API">
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
    "yaml": "service:\n  name: Kustomize\n  identifier: Kustomize\n  serviceDefinition:\n    type: Kubernetes\n    spec:\n      manifests:\n        - manifest:\n            identifier: kustomize\n            type: Kustomize\n            spec:\n              store:\n                type: Github\n                spec:\n                  connectorRef: Kustomize\n                  gitFetchType: Branch\n                  folderPath: kustomize/helloworld\n                  branch: main\n              pluginPath: \"\"\n              skipResourceVersioning: false\n  gitOpsEnabled: false"
  }
]
```
</details>

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Terraform Provider" label="Terraform Provider">
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
                name: Kustomize
                identifier: Kustomize
                serviceDefinition:
                  type: Kubernetes
                  spec:
                    manifests:
                      - manifest:
                          identifier: kustomize
                          type: Kustomize
                          spec:
                            store:
                              type: Github
                              spec:
                                connectorRef: Kustomize
                                gitFetchType: Branch
                                folderPath: kustomize/helloworld
                                branch: main
                            pluginPath: ""
                            skipResourceVersioning: false
                gitOpsEnabled: false
            EOT
}
```
</details>

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Pipeline Studio" label="Pipeline Studio">
```

To add a kustomization, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Manifests**, click **Add Manifest**.
7. In your CD stage, click **Service**.
8. In **Service Definition**, select **Kubernetes**.
9. In **Manifests**, click **Add Manifest**.
10. In **Specify Manifest Type**, click **Kustomize**, and click **Continue**.
11. In **Specify Manifest Type**, select a Git provider, [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/), or Azure Repo.
12. In **Manifest Details**, enter the following settings, test the connection, and click **Submit**.

    + **Manifest Identifier:** enter **kustomize**.
    + **Git Fetch Type:** select **Latest from Branch**.
    + **Branch:** enter **main**.
    + **Kustomize Folder Path:** kustomize/helloWorld. This is the path from the repo root.
    
    The kustomization is now listed.
    
    ![](./static/kubernetes-services-06.png)

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Kustomize Patches" label="Kustomize Patches" default>
```

You cannot use Harness variables in the base manifest or kustomization.yaml. You can only use Harness variables in kustomize patches you add in **Kustomize Patches Manifest Details**.

**How Harness uses patchesStrategicMerge:** 

- Kustomize patches override values in the base manifest. Harness supports the `patchesStrategicMerge` patches type.
- If the `patchesStrategicMerge` label is missing from the kustomization YAML file, but you have added Kustomize Patches to your Harness Service, Harness will add the Kustomize Patches you added in Harness to the `patchesStrategicMerge` in the kustomization file. If you have hardcoded patches in `patchesStrategicMerge`, but not add these patches to Harness as Kustomize Patches, Harness will ignore them.

For a detailed walkthrough of using patches in Harness, go to [Use Kustomize for Kubernetes deployments](../../cd-advanced/kustomize-howtos/use-kustomize-for-kubernetes-deployments.md).

To use Kustomize Patches, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Manifests**, select **Add Manifest**.
7. In **Specify Manifest Type**, select **Kustomize Patches**, and select**Continue**.
8. In **Specify Kustomize Patches Store**, select your Git provider and Connector. See [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md).
   
   The Git Connector should point to the Git account or repo where you Kustomize files are located. In **Kustomize Patches** you will specify the path to the actual patch files.
9.  Select **Continue**.
10. In **Manifest Details**, enter the path to your patch file(s):
    + **Manifest Identifier:** enter a name that identifies the patch file(s). You don't have to add the actual filename.
    + **Git Fetch Type:** select whether to use the latest branch or a specific commit Id.
    + **Branch**/**Commit Id**: enter the branch or commit Id.
    + **File/Folder Path:** enter the path to the patch file(s) from the root of the repo.
11. Click **Add File** to add each patch file. The files you add should be the same files listed in `patchesStrategicMerge` of the main kustomize file in your Service.
    
    The order in which you add file paths for patches in **File/Folder Path** is the same order that Harness applies the patches during the kustomization build.
    
    Small patches that do one thing are recommended. For example, create one patch for increasing the deployment replica number and another patch for setting the memory limit.
12. Select **Submit**. The patch file(s) is added to **Manifests**.
    
    When the main kustomization.yaml is deployed, the patch is rendered and its overrides are added to the deployment.yaml that is deployed.



```mdx-code-block
  </TabItem2>  
</Tabs2>
```

If this is your first time using Harness for a Kustomize deployment, see the [Kustomize Quickstart](../../onboard-cd/cd-quickstarts/kustomize-quickstart.md).

For a detailed walkthrough of deploying Kustomize in Harness, including limitations, see [Use Kustomize for Kubernetes Deployments](../../cd-advanced/kustomize-howtos/use-kustomize-for-kubernetes-deployments.md).
