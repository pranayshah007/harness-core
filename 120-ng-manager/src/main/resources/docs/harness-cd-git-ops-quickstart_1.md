# What is GitOps?

GitOps automates Kubernetes-based deployments by syncing declarative specifications (your manifests) with your target environments. In GitOps, Git is the single source of truth for the desired state of a cluster and its applications. GitOps continually converges the target state (cluster) in accordance with the desired state (manifests). This method turns your deployed applications and infrastructures into fully-traceable and fully-versioned artifacts.

You set up Harness GitOps by installing a GitOps Agent in your environment. Next, you define how to manage the desired and target state in a GitOps Application in Harness. The GitOps Agent performs the sync operations defined in the Application and reacts to events in the source and target states.

![](./static/harness-cd-git-ops-quickstart-00.png)

This quickstart shows you how to set up Harness GitOps using one of your Kubernetes clusters.
