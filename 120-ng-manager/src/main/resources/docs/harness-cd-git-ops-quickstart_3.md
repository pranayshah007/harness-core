# Before You Begin

* (Optional) **GitHub and DockerHub account:** this quickstart uses a publicly available manifest in GitHub and a public Docker image. You will be using anonymous credentials for connection, but you can use your own credentials if you like.  
We'll be using public manifests from <https://github.com/argoproj/argocd-example-apps>.
* **Target Kubernetes cluster:** you can use a cluster in any cloud platform. Harness makes a platform-agnostic connection to the cluster.

* **Set up your Kubernetes Cluster:** you'll need a target Kubernetes cluster for the Harness GitOps Agent and deployment. Ensure your cluster meets the following requirements:
  * **Number of nodes:** 2.
  * **vCPUs, Memory, Disk Size:** the Harness GitOps Agent only needs 1vCPUs, 2GB memory, 20GB disk, but you'll also be running Kubernetes and the deployed service.  
  A cluster with 2vCPUs, 8GB memory, 50GB disk is sufficient. In GKE, the **e2-standard-2** machine type is enough for this quickstart.
  * **Networking:** outbound HTTPS for the Harness connection to **app.harness.io**, **github.com**, and **hub.docker.com**. Allow TCP port 22 for SSH.
  * A **Kubernetes service account** with the permissions need to create your desired state. The Harness GitOps Agent requires either `cluster-admin` or admin permissions in the target namespace:
  	+ Create Deployment, Service, StatefulSet, Network Policy, Service Account, Role, ClusterRole, RoleBinding, ClusterRoleBinding, ConfigMap, Secret.
  	+ Permission to apply CustomResourceDefinition.  
  	For more information, see [User-Facing Roles](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#user-facing-roles) from Kubernetes.
