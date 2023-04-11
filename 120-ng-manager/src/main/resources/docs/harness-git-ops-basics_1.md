# Harness GitOps Summary

**What is GitOps?** GitOps is simply using Git to perform Operations Team (Ops) tasks, such as building infrastructure, release automation, and orchestration.

GitOps is also a way to perform CD using Git as the source of truth for the desired state of the deployment. Architecturally, GitOps uses an operator of some kind to continuously monitor the desired state in Git and and sync it with the live state in the Kubernetes cluster.

In contrast to using Pipeline steps to perform deployments, GitOps automates deployments by syncing declarative specifications (your manifests) with your target environments. GitOps continually converges the target state (cluster) in accordance with the desired state (manifests). This method turns your deployed applications and infrastructures into fully-traceable, fully-versioned artifacts.

The declarative description can be in Kubernetes manifest, Helm Charts, Kustomize manifests, and so on.

The live state can be any microservice or application and its Kubernetes environment.

Here's a very simple diagram of the GitOps architecture:

![](./static/harness-git-ops-basics-26.png)

The Harness GitOps Agent is a worker process installed in a Kubernetes cluster. The Agent can be installed in your target cluster or any cluster with connectivity to the target cluster.

The Harness GitOps Application runs in Harness SaaS and is where you select the source and target resources to use and how to sync them.

The GitOps Agent makes outbound connections to the GitOps Application in Harness and the Git source repo.

The GitOps Agent then syncs the desired state of the source manifest with the live state of the cluster.

**No Cluster-to-Git Push:** in Harness GitOps, the Git manifest is always the source of truth. GitOps does not perform cluster reconciliation (or *Self Heal*), a process where changes made to the cluster are pushed to the Git source.
