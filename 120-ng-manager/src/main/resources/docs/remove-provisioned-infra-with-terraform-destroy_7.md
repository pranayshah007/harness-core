# Step 2: Configuration Type

There are three options:

* **Inline:** Removes the provisioned resources you identify using **Provisioner Identifier** and other settings.
* **Inherit from Plan:** Removes the resources defined in the Harness **Terraform Plan** step that you identify using **Provisioner Identifier**. Similar to `terraform plan -destroy`.
* **Inherit from Apply:** Removes the resources defined in the Harness Terraform Apply step that you identify using **Provisioner Identifier**. Similar to `terraform destroy`.
