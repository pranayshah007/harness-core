### Secret Manager

* Select a Harness [secrets manager](https://developer.harness.io/docs/platform/Security/harness-secret-manager-overview) to use for encrypting/decrypting and saving the Terragrunt plan file.

A Terragrunt plan is a sensitive file that could be misused to alter resources if someone has access to it. Harness avoids this issue by never passing the Terragrunt plan file as plain text.

Harness only passes the Terragrunt plan between the Harness Manager and delegate as an encrypted file using a secrets manager.

When the `terragrunt plan` command runs on the Harness delegate, the delegate encrypts the plan and saves it to the secrets manager you selected. The encrypted data is passed to the Harness Manager.

When the plan is applied, the Harness manager passes the encrypted data to the delegate.

The delegate decrypts the encrypted plan and runs it.

Your Terragrunt Plan step is now ready. 

You can now configure a Terragrunt Apply, Destroy, or Rollback step to use the Terragrunt script from this Terragrunt Plan step.

```mdx-code-block
  </TabItem>
  <TabItem value="Terragrunt Apply" label="Terragrunt Apply">
```


To add a Terragrunt Apply step, do the following:

1. In your CD stage Execution, click **Add Step**, and then click **Terragrunt Apply**.
2. Enter the following Terragrunt Apply settings.
