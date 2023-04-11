# Requirements

To perform this tutorial you'll need the following:

* 3 Kubernetes clusters with a minimum of 2 vCPUs, 8GB Memory, 100 GB disk size (for example the [e2-standard-2](https://cloud.google.com/compute/docs/general-purpose-machines#e2_limitations) in GCP):
	+ 2 target clusters: the ApplicationSet will deploy an app to these 2 clusters.
		- For this tutorial, we will name the clusters **engineering-dev** and **engineering-prod**.  
		We will use the Kubernetes default namespace for the applications, but you can use any namespace.
	- 1 cluster for the Harness GitOps Agent. You can install the Agent in a cluster with or without an existing Argo CD project.
* GitHub account. You will be cloning the Argo Project's [ApplicationSet repo](https://github.com/argoproj/applicationset) and using one of its examples.
