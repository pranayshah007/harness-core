# Adding OpenShift Templates

OpenShift templates are added in the **Manifests** section of a Deploy Stage Service.

Add an OpenShift TemplateIn your CD stage, click **Service**.

In **Service Definition**, select **Kubernetes**.

In **Manifests**, click **Add Manifest**.

In **Specify Manifest Type**, select **OpenShift Template**, and then click **Continue.**

In **Specify OpenShift Template Store**, select the Git provider where your template is located.

For example, click **GitHub**, and then select or create a new GitHub Connector. See [Connect to Code Repo](../../../platform/7_Connectors/connect-to-code-repo.md).

Click **Continue**. **Manifest Details** appears.

In **Manifest Identifier**, enter an Id for the manifest. It must be unique. It can be used in Harness expressions to reference this template's settings.

For example, if the Pipeline is named **MyPipeline** and **Manifest Identifier** were **myapp**, you could reference the **Branch** setting using this expression:

`<+pipeline.stages.MyPipeline.spec.serviceConfig.serviceDefinition.spec.manifests.myapp.spec.store.spec.branch>`

In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit Id/Git Tag**, and then enter the branch or commit Id/[tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging) for the repo.

In **Template** **File Path**, enter the path to the template file. The Connector you selected already has the repo name, so you simply need to add the path from the root of the repo to the file.

Click **Submit**. The template is added to **Manifests**.
