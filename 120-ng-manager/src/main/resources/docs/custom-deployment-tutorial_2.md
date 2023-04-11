# Custom deployment using Deployment Templates summary

Here is a summary of the steps for setting up custom deployments using Deployment Templates:

1. Create a Deployment Template.
	1. In the template, include a script that returns a JSON array containing a list of the target instances where Harness will deploy your artifact.
	2. Identify the array path to the host object in the JSON so Harness can locate these at deployment runtime.
	3. Map any important host attributes that you want to reference later, like IP, region, etc.
2. Create a Harness Pipeline stage using the Deployment Template type and the Deployment Template.
	1. In your Harness Pipeline stage Service, create a Harness Service that uses the Deployment Template. Add the artifact you want to deploy.
	2. In your Harness Pipeline stage Environment, create a Harness Infrastructure Definition that uses the Deployment Template.
	3. In your Harness Pipeline stage Execution, review the automatically added **Fetch Instances** step, and move it to wherever you want to execute the script from your Deployment Template.
	4. Add a deployment step to Execution to deploy your artifact to the instances retrieved using the **Fetch Instances** step.

That's it. Your Pipeline will fetch the target instances as you requested and deploy your artifact to them.
