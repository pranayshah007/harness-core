## Scope of Expression

JSON representation of the Terraform plan is available only between the Terraform Plan step and subsequent Terraform Apply step. The expression will fail to resolve if used after the Terraform Apply step.

If used across stages, the Terraform Plan step can be used in one stage and the Terraform Apply step can be used in a subsequent stage. The expression will resolve successfully in this case.

The JSON of the Terraform Plan step is not available after Rollback.
