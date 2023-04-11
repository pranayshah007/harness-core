## Configuration Type

There are two options:

* **Inline:** removes the resources you identify using these settings:
	+ **AWS Connector:** add or select the Harness AWS Connector for connecting to AWS. Ensure its credentials have the permissions needed to remove the resources. See [AWS Connector](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md).
	+ **Region:** select the region for the resources you are removing.
	+ **Role ARN:** enter the AWS Role ARN to use when deleting the stack. This is the same as the role you would use when deleting a stack using the AWS console or CLI.
	+ **Stack Name:** enter the name of the stack to delete.
* **Inherit from Create:** removes the resources defined in the Harness **Create Stack** step that you identify using the same **Provisioner Identifier**.

![](./static/remove-provisioned-infra-with-the-cloud-formation-delete-step-06.png)
