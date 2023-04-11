# Review: Kustomize and Harness Delegates

All Harness Delegates include Kustomize by default. There is no installation required.

Your Delegate hosts, typically a pod in the target cluster, require outbound HTTPS/SSH connectivity to Harness and your Git repo.

The Delegate you use for Kustomize deployments must have access to the Git repo containing your Kustomize and resource files.The remainder of this topic assumes you have a running Harness Delegate and Cloud Provider Connector.

For details on setting those up, see:

* [Delegate Installation Overview](../../../first-gen/firstgen-platform/account/manage-delegates/delegate-installation-overview.md)
* [Define Your Kubernetes Target Infrastructure](../../cd-infrastructure/kubernetes-infra/define-your-kubernetes-target-infrastructure.md)
