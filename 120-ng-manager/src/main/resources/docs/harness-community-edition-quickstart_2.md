# Requirements

* **Harness CD Community Edition:**
	+ Docker Desktop minimum version 4.3.1 (72247)
		- 3GB RAM and 2 CPUs is the minimum.
		- Docker Compose (included in Docker Desktop).
	+ 20GB of free disk space.
* **Tutorial:**
	+ GitHub account.
  	+ You will use your GitHub account to pull a publicly available manifest (`https://github.com/kubernetes/website/blob/main/content/en/examples/application/nginx-app.yaml`).
	+ Docker Compose Kubernetes is installed and running in Docker Desktop (a new installation of Docker Desktop might need to have Kubernetes enabled in its **Settings**).  
	  - Docker Compose Kubernetes should have at least 2GB memory and 1 CPU. That will bring the total Docker Desktop resources up to a minimum of **5GB and 3 CPUs**.
	  - If you want to use Minikube instead of Docker Desktop Kubernetes, use Minikube minimum version v1.22.0 or later installed locally.
  	  - Minikube needs 4GB and 4 CPUs: `minikube start --memory 4g --cpus 4`.
	+ Kubernetes cluster.
  	+ This is the target cluster for the deployment you will set up in this quickstart. When Docker Compose Kubernetes is installed it comes with a cluster and the **default** namespace. You don't need to make any changes to Docker Compose Kubernetes.Don't have a cluster? See [Notes](#notes).
	+ Review [Harness CD Community Edition Overview](../../cd-advanced/cd-kubernetes-category/harness-community-edition-overview.md) and [Harness Key Concepts](../../../first-gen/starthere-firstgen/harness-key-concepts.md) to establish a general understanding of Harness.

The Docker Compose installer is described below, but Harness also supports a [Helm installer](https://github.com/harness/harness-cd-community/blob/main/helm/README.md).
