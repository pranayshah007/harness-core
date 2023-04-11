# Install the GitOps Agent

For steps on how to install a Harness GitOps Agent, go to [Install a Harness GitOps Agent](install-a-harness-git-ops-agent.md).

1. Install the Agent in the Kubernetes cluster you have set up for your GitOps Agent, not the target dev or prod target clusters.

Ensure the Agent has access to Harness and to the other 2 target clusters. Once it's installed you'll see it register with Harness:

![](./static/harness-git-ops-application-set-tutorial-27.png)

:::note

**Mapped Harness Project:** if you installed the Agent in a cluster without an existing Argo CD project, there will not be a mapping initially. Once you create a Harness GitOps entity using the Agent, such as a Cluster or Repo, Harness will automatically create the Argo CD project and map it to the Agent.

:::
