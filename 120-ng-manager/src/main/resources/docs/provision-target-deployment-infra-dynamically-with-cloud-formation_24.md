# Map outputs to target infra settings

Now that the Create Stack step is set up in **Dynamic provisioning**, Harness is configured to provision the infrastructure defined in your CloudFormation template.

There are two options:

1. If you are simply provisioning a resource in your deployment infrastructure that is not intended as the deployment target, such as an AWS secret, you can define the deployment target in **Infrastructure Definition** as you would if you were not provisioning anything.
2. If you will deploy directly into the provisioned resource as part of the deployment target, you need to provide the required **Infrastructure Definition** settings so Harness can target and deploy to the provisioned infrastructure.

We'll cover option 2.

The required settings are specific outputs from your CloudFormation template. Which settings are required depends on the type of target infrastructure you are provisioning/targeting.

For example, a platform-agnostic Kubernetes cluster infrastructure only requires the target namespace in the target cluster.

To map the CloudFormation template output to the Infrastructure Definition setting, ensure that the template has the CloudFormation Output defined.

In the **Infrastructure Definition** setting, select **Expression**:

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-05.png)

In the setting, enter a Harness expression that references the output.

The expressions follow the format:

`<+infrastructureDefinition.provisioner.steps.[Create Stack step Id].output.[output name]>`

You can find the Id in the step:

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-06.png)

For example, for a Kubernetes deployment, you need to map the `namespace` output to the **Namespace** setting in Infrastructure Definition.

So for a Create Stack step with the Id **create123** and an output named **namespace**, the expression is:

`<+infrastructureDefinition.provisioner.steps.create123.output.namespace>`

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-07.png)

Now Harness has the provisioned target infrastructure set up.
