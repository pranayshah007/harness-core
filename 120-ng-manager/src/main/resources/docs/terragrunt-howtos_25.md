#### Provisioner Identifier scope

The **Provisioner Identifier** is a project-wide setting. You can reference it across pipelines in the same project.

For this reason, it's important that all your project members know the provisioner identifiers. Sharing this information will prevent one member building a pipeline from accidentally impacting the provisioning of another member's pipeline.

```mdx-code-block
  </TabItem>
  <TabItem value="Terragrunt Rollback" label="Terragrunt Rollback">
```

To add a Terragrunt Rollback step, do the following:

1. In your CD stage Execution, click Rollback.
2. Click **Add Step**, and then click **Terragrunt Rollback**.
3. Enter the following Terragrunt Rollback settings.
