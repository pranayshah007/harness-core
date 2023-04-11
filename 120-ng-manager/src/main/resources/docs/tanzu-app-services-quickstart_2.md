# Important notes

* For TAS deployments, Harness supports these artifact sources: Artifactory, Nexus, Amazon S3, Google Container Registry (GCR), Amazon Elastic Container Registry (ECR), Azure Container Registry (ACR), Google Artifact Registry, GitHub Package Registry, Custom registry, and any Docker Registry such as DockerHub. You connect Harness to these registries by using your registry account credentials.
* Before you create a TAS pipeline in Harness, make sure that you have the **Continuous Delivery** module in your Harness account. For more information, go to [create organizations and projects](https://developer.harness.io/docs/platform/organizations-and-projects/create-an-organization/). 
* Your Harness delegate profile must have [CF CLI v7, `autoscaler`, and `Create-Service-Push` plugins](#install-cloud-foundry-command-line-interface-cf-cli-on-your-harness-delegate) added to it. 
