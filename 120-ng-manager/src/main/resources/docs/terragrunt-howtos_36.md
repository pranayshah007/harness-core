## Terraform Var Files

* In **Terraform Var Files**, provide values for the Terraform input variables in the Terraform module (config.tf) that your Terragrunt config file uses.

For example, here's a Terraform config.tf file with variables for access and secret key:

```
variable "access_key" {}
variable "secret_key" {}  
  
provider "aws" {  
  access_key = var.access_key  
  secret_key = var.secret_key  
  region = "us-east-1"  
}  
...
```

You can use inline or remote tfvar settings.

If you use inline, you can paste in the input variables, like this:

```
count_of_null_resources = "7"
file_message = "testing tvar"
```

You can Harness [variable](https://developer.harness.io/docs/platform/Variables-and-Expressions/harness-variables) and [secret](https://developer.harness.io/docs/first-gen/firstgen-platform/security/secrets-management/use-encrypted-text-secrets) expressions in the inputs also.

