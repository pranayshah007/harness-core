## Backend Configuration

Depending on which platform you store your remote state data, Terragrunt and Terraform allow you to pass many different credentials and configuration settings, such as access and secret keys. 

For example:

```
resource_group_name  = "tfResourceGroup"
storage_account_name = "myterraformremoteback"
container_name       = "azure-backend"
```

For examples, see the settings available for [AWS S3](https://www.terraform.io/docs/backends/types/s3.html#configuration) from Terraform and review [Keep your remote state configuration DRY](https://terragrunt.gruntwork.io/docs/features/keep-your-remote-state-configuration-dry/) from Terragrunt.

* In **Backend Configuration**, enter values for each backend config (remote state variable) in the Terragrunt config (.hcl) or Terraform script (config.tf) file.
