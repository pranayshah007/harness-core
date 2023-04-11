# Review: Argo CD and Harness Project Mapping

Once you have installed the Agent, Harness will create its own Argo CD Project in the cluster and name it with a random string.

**Once you use this Agent to add another entity,** such as a Cluster or Repository, Harness will then map this new Argo CD project to a Harness Project identifier (Id). You will see this mapping in Harness:

![](./static/install-a-harness-git-ops-agent-93.png)

If you used an existing Argo CD Project, you will see the existing Argo CD Project mapped to your Harness Project Id:

![](./static/install-a-harness-git-ops-agent-94.png)

See [Entity Identifier Reference](../../platform/20_References/entity-identifier-reference.md).
