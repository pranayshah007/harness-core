## Module Configuration

Use this setting to specify the Terraform modules you want Terragrunt to use.

In **Module Configuration**, you are telling Harness where to locate your [terragrunt.hcl file](https://terragrunt.gruntwork.io/docs/getting-started/configuration/). The terragrunt.hcl itself will point to a Terraform module using the source parameter like this:

```
locals {  
}  
  
terraform {  
//  source = "git::git@github.com:Tathagat-289/terraformResources.git//module3"  
  source = "github.com/Tathagat-289/terraformResources//module3"  
}  
  