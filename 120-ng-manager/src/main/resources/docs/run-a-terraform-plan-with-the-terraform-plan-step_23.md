## Using a Remote State File

:::note

Currently, remote state file support is behind the feature flag `TERRAFORM_REMOTE_BACKEND_CONFIG`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

1. In Backend Configuration, select **Remote**.
2. Click **Specify Backend Config File**, add a Connector to your repo, and select the backend config file.  

![](./static/run-a-terraform-plan-with-the-terraform-plan-step-15.png)

You can also use files in the [Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md).

To use the same remote state file set in the Terraform Plan step, Terraform Apply steps must use the same Provisioner Identifier.

For an example of how the config file should look, please see [Backend Configuration](https://developer.hashicorp.com/terraform/language/settings/backends/configuration#file) from HashiCorp.

Here's an example.

main.tf (note the empty backend S3 block):


```json
variable "global_access_key" {type="string"}  
variable "global_secret_key" {type="string"}  
  
variable "env" {  
    default= "test"  
}  
provider "aws" {  
  access_key = "${var.global_access_key}"  
  secret_key = "${var.global_secret_key}"  
  region = "us-east-1"  
      
}  
  
resource "aws_s3_bucket" "bucket" {  
  bucket = "prannoy-test-bucket"  
}  
  
terraform {  
backend "s3" {  
      
  }  
}
```


backend.tf:

```json
access_key = "1234567890"  
secret_key = "abcdefghij"  
bucket = "terraform-backend-config-test"  
key    = "remotedemo.tfstate"  
region = "us-east-1"
```
