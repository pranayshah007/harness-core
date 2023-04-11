## Tags

The **Value** setting in **Tags** is a runtime input. You can select the tag to use when the pipeline using this artifact source template executes.

You can also use **Regex**.

For example, if the build is `todolist-v2.0`, the regex `todolist-v\d.\d` will match.

If the regex expression does not result in a match, Harness ignores the value.

Harness supports standard Java regex. For example, if you enable **Regex** with the intent is to match a filename, the wildcard must be `.*` instead of only `*`. Similarly, if you want to match all files ending in `-DEV.tgz`, your wildcard regex phrase would be: `.*-DEV\.tgz`

```mdx-code-block
  </TabItem>
  <TabItem value="YAML" label="YAML">
```

1. In your template, select **YAML**
2. Paste the following YAML example and select **Save**:

```yaml
template:
  name: Todo List
  identifier: Todo_List
  versionLabel: "1"
  type: ArtifactSource
  projectIdentifier: CD_Docs
  orgIdentifier: default
  tags: {}
  spec:
    type: DockerRegistry
    spec:
      imagePath: harness/todolist-sample
      tag: <+input>
      connectorRef: Docker_Hub_with_Pwd

```

Note that `connectorRef` refers to the Id of a Harness connector.

```mdx-code-block
  </TabItem>
</Tabs>
```
