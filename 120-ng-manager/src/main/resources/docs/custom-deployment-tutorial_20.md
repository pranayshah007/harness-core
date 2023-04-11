## Deployment Template Infrastructure variables

You can create the following types of variables.

![](./static/custom-deployment-tutorial-32.png)

* **String:** any string you might want to reference in your stage steps.
* **Secret:** select or create a secret in Harness using Harness default Secret Manager or a Secret Manager you have configured in Harness. For more information, go to [Secrets and Secret Management](/docs/category/security).
* **Number:** a number you might want to reference in your stage steps.
* **Connector:** a Harness Connector that you want to use for this deployment. Any Connector can be used, such as an infrastructure Connector, like an AWS Connector, or a repo Connector for your manifests and specs, like a GitHub Connector.
