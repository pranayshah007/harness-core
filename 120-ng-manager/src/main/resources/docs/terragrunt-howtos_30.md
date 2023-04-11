### Notes

The following notes discuss Terragrunt rollback scenarios.

- Deployment rollback.
  - If you have successfully deployed Terraform modules and on the next deployment there is an error that initiates a rollback, Harness will roll back the provisioned infrastructure to the previous, successful version of the Terraform state.
  - Harness will not increment the serial in the state, but perform a hard rollback to the exact version of the state provided.
- Rollback limitations.
  - If you deployed two modules successfully already, module1 and module2, and then attempted to deploy module3, but failed, Harness will roll back to the successful state of module1 and module2.
  - However, let's look at the situation where module3 succeeds and now you have module1, module2, and module3 deployed. If the next deployment fails, the rollback will only roll back to the Terraform state with module3 deployed. Module1 and module2 were not in the previous Terraform state, so the rollback excludes them.

```mdx-code-block
  </TabItem>    
</Tabs>
```