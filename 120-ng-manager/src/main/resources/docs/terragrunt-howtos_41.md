#### Scope of expression

**Export JSON representation of the Terragrunt Plan** is available only between the Terragrunt Plan step and subsequent Terragrunt Apply or Destroy steps. The expression will fail to resolve if used after the Terragrunt Apply or Destroy steps.

If used across stages, the Terragrunt Plan step can be used in one stage and the Terragrunt Apply or Destroy step can be used in a subsequent stage. The expression will resolve successfully in this case.

The JSON of the Terragrunt Plan step is not available after rollback.
