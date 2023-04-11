## Kubernetes

<details>
<summary>Use Kubernetes manifests</summary>

You can use:

- Standard Kubernetes manifests hosted in any repo or in Harness.
- Values YAML files that use Go templating to template manifests.
- Values YAML files can use a mix of hardcoded values and Harness expressions.

<details>
<summary>Watch a short video</summary>
Here's a quick video showing you how to add manifests and Values YAML files in Harness. It covers Kubernetes as well as other types like Helm Charts.

 
<!-- Video:
https://www.youtube.com/watch?v=dVk6-8tfwJc-->
<docvideo src="https://www.youtube.com/watch?v=dVk6-8tfwJc" />
</details>


```mdx-code-block
<Tabs>
  <TabItem value="YAML" label="YAML">
```
Here's a YAML example for a service with manifests hosted in Github and the nginx image hosted in Docker Hub.

<details>
<summary>Example</summary>

```yaml
service:
  name: Kubernetes
  identifier: Kubernetes
  serviceDefinition:
    type: Kubernetes
    spec:
      artifacts:
        primary:
          primaryArtifactRef: <+input>
          sources:
            - spec:
                connectorRef: Docker_Hub_with_Pwd
                imagePath: library/nginx
                tag: <+input>
              identifier: nginx
              type: DockerRegistry
      manifests:
        - manifest:
            identifier: nginx
            type: K8sManifest
            spec:
              store:
                type: Github
                spec:
                  connectorRef: harnessdocs2
                  gitFetchType: Branch
                  paths:
                    - default-k8s-manifests/Manifests/Files/templates
                  branch: main
              valuesPaths:
                - default-k8s-manifests/Manifests/Files/ng-values.yaml
              skipResourceVersioning: false
  gitOpsEnabled: false
```
</details>

```mdx-code-block
  </TabItem>
  <TabItem value="API" label="API">
```

Create a service using the [Create Services](https://apidocs.harness.io/tag/Services#operation/createServicesV2) API.

<details>
<summary>Services API example</summary>

```json
curl -i -X POST \
  'https://app.harness.io/gateway/ng/api/servicesV2/batch?accountIdentifier=<Harness account Id>' \
  -H 'Content-Type: application/json' \
  -H 'x-api-key: <Harness API key>' \
  -d '[{
    "identifier": "KubernetesTest",
    "orgIdentifier": "default",
    "projectIdentifier": "CD_Docs",
    "name": "KubernetesTest",
    "description": "string",
    "tags": {
      "property1": "string",
      "property2": "string"
    },
    "yaml": "service:\n  name: KubernetesTest\n  identifier: KubernetesTest\n  serviceDefinition:\n    type: Kubernetes\n    spec:\n      artifacts:\n        primary:\n          primaryArtifactRef: <+input>\n          sources:\n            - spec:\n                connectorRef: account.harnessImage\n                imagePath: library/nginx\n                tag: stable-perl\n              identifier: nginx\n              type: DockerRegistry\n      manifests:\n        - manifest:\n            identifier: myapp\n            type: K8sManifest\n            spec:\n              store:\n                type: Harness\n                spec:\n                  files:\n                    - /Templates\n              valuesPaths:\n                - /values.yaml\n              skipResourceVersioning: false\n              enableDeclarativeRollback: false\n  gitOpsEnabled: false"
  }]'
```
</details>



```mdx-code-block
  </TabItem>  
  <TabItem value="Terraform Provider" label="Terraform Provider">
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
                  name: name
                  identifier: identifier
                  serviceDefinition:
                    spec:
                      manifests:
                        - manifest:
                            identifier: manifest1
                            type: K8sManifest
                            spec:
                              store:
                                type: Github
                                spec:
                                  connectorRef: <+input>
                                  gitFetchType: Branch
                                  paths:
                                    - files1
                                  repoName: <+input>
                                  branch: master
                              skipResourceVersioning: false
                      configFiles:
                        - configFile:
                            identifier: configFile1
                            spec:
                              store:
                                type: Harness
                                spec:
                                  files:
                                    - <+org.description>
                      variables:
                        - name: var1
                          type: String
                          value: val1
                        - name: var2
                          type: String
                          value: val2
                    type: Kubernetes
                  gitOpsEnabled: false
              EOT
}
```
</details>


```mdx-code-block
  </TabItem>  
  <TabItem value="Pipeline Studio" label="Pipeline Studio">
```
To add Kubernetes manifests to your service, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Manifests**, click **Add Manifest**.
7. In **Specify Manifest Type**, select **K8s Manifest**, and then click **Continue**.
8. In **Specify K8s Manifest Store**, select the Git provider.
   
   The settings for each Git provider are slightly different, but you simply want to point to the Git account For example, click GitHub, and then select or create a new GitHub Connector. See [Connect to Code Repo](../../../platform/7_Connectors/connect-to-code-repo.md).
9.  Click **Continue**. **Manifest Details** appears.
10. In **Manifest Identifier**, enter an Id for the manifest.
11. If you selected a Connector that uses a Git account instead of a Git repo, enter the name of the repo where your manifests are located in **Repository Name**.
12. In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit ID**, and then enter the branch or commit Id for the repo.
13. For **Specific Commit ID**, you can also use a [Git commit tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging).
14. In **File/Folder Path**, enter the path to the manifest file or folder in the repo. The Connector you selected already has the repo name, so you simply need to add the path from the root of the repo.
    
    If you are using a values.yaml file and it's in the same repo as your manifests, in **Values YAML**, click **Add File**.
15. Enter the path to the values.yaml file from the root of the repo.
    
    Here's an example with the manifest and values.yaml file added.
    
    ![](./static/kubernetes-services-01.png)
    
    If you use multiple files, the highest priority is given from the last file, and the lowest priority to the first file. For example, if you have 3 files and the second and third files contain the same key:value as the first file, the third file's key:value overrides the second and first files.
    
    ![](./static/kubernetes-services-02.png)
16. Click **Submit**. The manifest is added to **Manifests**.


```mdx-code-block
  </TabItem>  
  <TabItem value="Values YAML" label="Values YAML">
```

Harness Kubernetes Services can use Values YAML files just like you would using Helm. Harness manifests can use [Go templating](#go_templating) with your Values YAML files and you can include [Harness variable expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) in the Values YAML files.

If you are using a Values YAML file and it's in the same repo as your manifests, you can add it when you add your manifests, as described above (**Values YAML** --> **Add File**).

If you are using a Values YAML file and it's in a separate repo from your manifests, or you simply want to add it separately, you can add it as a separate file, described below.

You cannot use Harness variables expressions in your Kubernetes object manifest files. You can only use Harness variables expressions in Values YAML files.Add a Values YAML fileWhere is your Values YAML file located?

* **Same folder as manifests:** If you are using a values.yaml file and it's in the same repo as your manifests, you can add it when you add your manifests, as described above (**Values YAML** --> **Add File**).
* **Separate from manifests:** If your values file is located in a different folder, you can add it separately as a **Values YAML** manifest type, described below.

To add a Values YAML file, do the following:

1. In your project, in CD (Deployments), select **Services**.
2. Select **Manage Services**, and then select **New Service**.
3. Enter a name for the service and select **Save**.
4. Select **Configuration**.
5. In **Service Definition**, select **Kubernetes**.
6. In **Manifests**, click **Add Manifest**.
7. In **Specify Manifest Type**, select **Values YAML**, and click **Continue.**
8. In **Specify Values YAML Store**, select the Git repo provider you're using and then create or select a Connector to that repo. The different Connectors are covered in [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md).
   
   If you haven't set up a Harness Delegate, you can add one as part of the Connector setup. This process is described in [Kubernetes CD tutorial](../../onboard-cd/cd-quickstarts/kubernetes-cd-quickstart.md), [Helm CD tutorial](../../onboard-cd/cd-quickstarts/helm-cd-quickstart.md) and [Install a Kubernetes delegate](../../../platform/2_Delegates/install-delegates/overview.md).
9.  Once you've selected a Connector, click **Continue**.
10. In **Manifest Details**, you tell Harness where the values.yaml is located.
11. In **Manifest Identifier**, enter a name that identifies the file, like **values**.
12. If you selected a Connector that uses a Git account instead of a Git repo, enter the name of the repo where your manifests are located in **Repository Name**.
13. In **Git Fetch Type**, select a branch or commit Id for the manifest, and then enter the Id or branch.
    * For **Specific Commit ID**, you can also use a [Git commit tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging).
    * In **File Path**, enter the path to the values.yaml file in the repo.
   
   You can enter multiple values file paths by clicking **Add File**. At runtime, Harness will compile the files into one values file.
   
   If you use multiple files, the highest priority is given from the last file, and the lowest priority to the first file. For example, if you have 3 files and the second and third files contain the same key:value as the first file, the third file's key:value overrides the second and first files.
   
   ![](./static/kubernetes-services-03.png)
14. Click **Submit**.

The values file(s) are added to the Service.
