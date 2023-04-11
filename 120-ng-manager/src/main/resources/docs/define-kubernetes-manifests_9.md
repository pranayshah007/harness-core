# Step 4: Add Kubernetes Manifests

You can use your Git repo for the configuration files in **Manifests** and Harness will use them at runtime.

If you are adding the image location to Harness as an Artifact in the Service Definition, see [Add Container Images as Artifacts for Kubernetes Deployments](add-artifacts-for-kubernetes-deployments.md).

1. In **Manifests**, click **Add Manifest**.
2. In **Specify Manifest Type**, select **K8s Manifest**, and then click **Next**.
3. In **Specify K8s Manifest Store**, select the Git provider. In this example, click GitHub, and then select or create a new GitHub Connector. See [Connect to Code Repo](../../../platform/7_Connectors/connect-to-code-repo.md).
4. Click **Continue**. **Manifest Details** appears.
   
   ![](./static/define-kubernetes-manifests-30.png)

5. In **Manifest Identifier**, enter an Id for the manifest. It must be unique. It can be used in Harness expressions to reference this manifests settings.
   
   For example, if the Pipeline is named **MyPipeline** and **Manifest Identifier** were **myapp**, you could reference the **Branch** setting using this expression:
   
   `<+pipeline.stages.MyPipeline.spec.serviceConfig.serviceDefinition.spec.manifests.myapp.spec.store.spec.branch>`

6. In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit ID**, and then enter the branch or commit ID for the repo.
7.  For **Specific Commit ID**, you can also use a [Git commit tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging).
8.  In **File/Folder Path**, enter the path to the manifest file or folder in the repo. The Connector you selected already has the repo name, so you simply need to add the path from the root of the repo.
    1.  **Add all manifests and values files in the folder:** If you enter a folder, Harness will automatically detect and use all of the manifests and values YAML files in that folder. Values files can also be added separately as a [Values YAML type](add-and-override-values-yaml-files.md).
2.  Click **Submit**. The manifest is added to **Manifests**.
