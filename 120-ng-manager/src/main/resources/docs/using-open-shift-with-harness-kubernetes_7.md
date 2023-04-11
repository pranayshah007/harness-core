# OpenShift Param Files

OpenShift Param Files are added in the **Manifests** section of a Deploy Stage Service.

Add an OpenShift Param FileIn your CD stage, click **Service**.

In **Service Definition**, select **Kubernetes**.

In **Manifests**, click **Add Manifest**.

In **Specify Manifest Type**, select **OpenShift Param**, and then click **Continue.**

In **Specify OpenShift Param Store**, select the Git provider where your param file is located.

For example, click **GitHub**, and then select or create a new GitHub Connector. See [Connect to Code Repo](../../../platform/7_Connectors/connect-to-code-repo.md).

Click **Continue**. **Manifest Details** appears.

In **Manifest Identifier**, enter an Id for the param file. It must be unique. It can be used in Harness expressions to reference this param file's settings.

In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit Id/Git Tag**, and then enter the branch or commit Id/[tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging) for the repo.

In **Paths**, enter the path(s) to the param file(s). The Connector you selected already has the repo name, so you simply need to add the path from the root of the repo to the file.

Click **Submit**. The template is added to **Manifests**.
