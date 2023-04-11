# Map outputs to target infra settings

Now that the Terraform Plan and Terraform Apply steps are set up in **Dynamic provisioning**, Harness is configured to provision the infrastructure defined in your Terraform script.

Next, in the **Infrastructure Definition**, you need to provide the required Infrastructure Definition settings so Harness can target and deploy to the provisioned infrastructure.

The required settings are specific outputs from your Terraform script. Which settings are required depends on the type of target infrastructure you are provisioning/targeting.

For example, a platform-agnostic Kubernetes cluster infrastructure only requires the target namespace in the target cluster.

Gather the following:

* The name you gave the Terraform Apply step.
* The names of the required outputs from your Terraform script.

Create the FQN expression for each output.

The expressions follow the format:

`<+infrastructureDefinition.provisioner.steps.[Apply Step Id].output.[output name]>`

For example, for a Kubernetes deployment, you need to map the `namespace` output to the **Namespace** setting in Infrastructure Definition.

So for a Terraform Apply step with the Id **apply123** and an output named **namespace**, the expression is:

`<+infrastructureDefinition.provisioner.steps.apply123.output.namespace>`

Here you can see how the expression is created from the Terraform Apply step name and the Terraform script (output.tf, config.tf, etc):

Use the expressions to map the outputs in the required Infrastructure Definition settings, such as **namespace** in **Cluster Details**.

<!-- ![](./static/provision-infra-dynamically-with-terraform-06.png) -->

<docimage path={require('./static/provision-infra-dynamically-with-terraform-06.png')} />

Now Harness has the provisioned target infrastructure set up.

You can complete your Pipeline and then run it.

Harness will provision the target infrastructure and then deploy to it.
