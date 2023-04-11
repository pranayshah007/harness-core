# Use artifact templates in CD services

After creating an artifact source template, you can add it as the artifact source of a Harness service.

To add an artifact source template to a service, do the following:

```mdx-code-block
import Tabs2 from '@theme/Tabs';
import TabItem2 from '@theme/TabItem';
```
```mdx-code-block
<Tabs2>
  <TabItem2 value="Visual" label="Visual" default>
```

1. Select the **Deployments** module.
2. Select **Services**.
3. Select **Manage Services**.
4. Select a service.
5. Select **Configuration**.
6. In **Service Definition**, in **Artifacts**, select **Use template**.
   
   ![use template](static/8818bc80c6c74f021eb456676680d3c2bb05208b033c045a8adce177ef93af55.png)

7. Select the artifact source template you want to use, and then select **Use Template**.
   
   ![template selected](static/d18f8cfaf6ad2a4e5f61e78d9d9eddff4df602507c8748463eb31c9e2339cb30.png)  

8. Enter a name for template.
9.  In **Tag**, you can change the setting to **Fixed Value**, and then select the tag to use. Or you can use a runtime input or expression.
10. Select **Apply Changes**. The template is added to the service.


```mdx-code-block
  </TabItem2>
  <TabItem2 value="YAML" label="YAML">
```

1. Select the **Deployments** module.
2. Select **Services**.
3. Select **Manage Services**.
4. Select a service.
5. Select **Configuration**.
6. Select **YAML**.
7. Paste the following YAML example and select **Save**:

```yaml
service:
  name: Example K8s
  identifier: Example_K8s
  serviceDefinition:
    type: Kubernetes
    spec:
      artifacts:
        primary:
          primaryArtifactRef: <+input>
          sources:
            - name: todolist
              identifier: todolist
              template:
                templateRef: Todo_List
                versionLabel: "1"
                templateInputs:
                  type: DockerRegistry
                  spec:
                    tag: <+input>
      manifests:
        - manifest:
            identifier: manifest
            type: K8sManifest
            spec:
              store:
                type: Harness
                spec:
                  files:
                    - /Templates/deployment.yaml
              skipResourceVersioning: false
  gitOpsEnabled: false

```

You can see the artifact source template in the `artifacts` section:

```
...
            - name: todolist
              identifier: todolist
              template:
                templateRef: Todo_List
                versionLabel: "1"
                templateInputs:
                  type: DockerRegistry
                  spec:
                    tag: <+input>
...
```

```mdx-code-block
  </TabItem2>
</Tabs2>
```


