## Using Inline State

Enter values for each backend config (remote state variable).

For example, if your config.tf file has the following backend:


```json
terraform {  
  backend "gcs" {  
    bucket  = "tf-state-prod"  
    prefix  = "terraform/state"  
  }  
}
```

In **Backend Configuration**, you provide the required configuration variables for that backend type. See **Configuration variables** in Terraform's [gcs Standard Backend doc](https://www.terraform.io/docs/language/settings/backends/gcs.html#configuration-variables).
