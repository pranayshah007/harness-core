## Configuration type

There are two options:

* **Inline:** similar to `aws cloudformation delete-stack --stack-name my-stack`. **Inline** removes the stack you identify using these settings:
	+ **AWS Connector:** add or select the Harness AWS Connector for connecting to AWS. Ensure its credentials have the permissions needed to remove the resources. See [AWS Connector](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md).
	+ **Region:** select the region for the resources you are removing.
	+ **Role ARN:** enter the AWS Role ARN to use when deleting the stack. This is the same as the role you would use when deleting a stack using the AWS console or CLI.
	+ **Stack Name:** enter the name of the stack to delete.
* **Inherit from Create:** removes the resources defined in the Harness **Create Stack** step that you identify using **Provisioner Identifier**.

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-04.png)
