## Targets

* In **Target**, target one or more specific modules in your Terraform script, just like using the `terraform plan -target`, `terraform apply -target`, or `terraform destory -target` commands. See [Resource Targeting](https://www.terraform.io/docs/commands/plan.html#resource-targeting) from Terraform.

  If you have multiple modules in your script and you do not select one in **Targets**, all modules are used.

  You can also use [runtime inputs or expressions](https://developer.harness.io/docs/platform/references/runtime-inputs/) for your targets. 

  For example, you can create a stage variable named `module` and then enter the variable `<+stage.variables.module>` in **Targets**. 
