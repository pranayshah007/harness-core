# Option: Harness GitOps Agent with Existing Argo CD Project

In **Getting started with Harness GitOps**, you have the option of installing a new Harness GitOps Agent with or without an existing Argo CD instances.

Click **Yes**, and then click **Start**.

In **Name**, enter the name for the existing Agent CD Project. For example, **default** in the this example:

![](./static/install-a-harness-git-ops-agent-89.png)

In **Namespace**, enter the namespace where you want to install the Harness GitOps Agent. Typically, this is the target namespace for your deployment.

Click **Next**. The **Review YAML** settings appear.

This is the manifest YAML for the Harness GitOps Agent. You will download this YAML file and run it in your Harness GitOps Agent cluster.

Once you have installed the Agent, Harness will start importing all the entities from the existing Argo CD Project.
