## Workspace

Harness supports Terraform workspaces.

A Terraform workspace allows you to maintain separate state files for different environments, such as dev, staging, and production. This way, you can run Terraform commands for each environment without impacting the state of the other environments.

When you use Terragrunt with Terraform workspaces, Terragrunt automatically creates and switches between the workspaces for each environment based on the configuration specified in the terragrunt.hcl file.

Workspaces are useful when testing changes before moving to a production infrastructure. To test the changes, you create separate workspaces for Dev and Production.

A workspace is really a different state file. Each workspace isolates its state from other workspaces. For more information, see [When to use Multiple Workspaces](https://www.terraform.io/docs/state/workspaces.html#when-to-use-multiple-workspaces) from Hashicorp.

Here is an example script where a local value names two workspaces, default and production, and associates different instance counts with each:

```
locals {  
  counts = {  
      "default"=1  
      "production"=3  
  }  
}  
  
resource "aws_instance" "my_service" {  
  ami="ami-7b4d7900"  
  instance_type="t2.micro"  
  count="${lookup(local.counts, terraform.workspace, 2)}"  
  tags {  
         Name = "${terraform.workspace}"  
    }  
}
```

In the workspace interpolation sequence you can see the count is assigned by applying it to the workspace variable (`terraform.workspace`) and that the tag is applied using the variable also.

* In **Workspace**, enter the name of the workspace to use.

    Terraform will pass the workspace name you provide to the `terraform.workspace` variable, thus determining the count. This is the same as the `terraform workspace select` command.
    
    Using the example above, if you provide the name `production`, the count will be 3.

    You can also set **Workspace** as a [runtime inputs or expression](https://developer.harness.io/docs/platform/references/runtime-inputs/) and use a different workspace name each time the pipeline is run.

