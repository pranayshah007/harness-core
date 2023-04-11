# Step: Add Values YAML

You can add one or more Values YAML files in two ways:

* **Same repo:** If the Values YAML are in the same repo as your Kubernetes manifests or Helm Charts, you can add them when you add those files. You simply use the Values YAML setting.  
![](./static/add-and-override-values-yaml-files-32.png)
* **Different repos:** If the Values YAML are in a repo separate from your Kubernetes manifests or Helm Charts, or you just want to add them separately, you can them by selecting the Values YAML manifest type.  

We'll cover this option below.

1. In your CD stage, click **Service**.
2. In **Service Definition**, select **Kubernetes**.
3. In **Manifests**, click **Add Manifest**.
4. In **Specify Manifest Type**, select **Values YAML**, and click **Continue.**
   
   ![](./static/add-and-override-values-yaml-files-33.png)

1. In **Specify Values YAML Store**, select the Git repo provider you're using and then create or select a Connector to that repo. The different Connectors are covered in [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md).
5. If you haven't set up a Harness Delegate, you can add one as part of the Connector setup.
    This process is described in [Kubernetes deployment tutorial](../../onboard-cd/cd-quickstarts/kubernetes-cd-quickstart.md), [Helm Chart deployment tutorial](../../onboard-cd/cd-quickstarts/helm-cd-quickstart.md) and [Install a Kubernetes Delegate](../../../platform/2_Delegates/install-delegates/overview.md).
1. Once you've selected a Connector, click **Continue**.
6. In **Manifest Details**, you tell Harness where the values.yaml is located.
7. In **Manifest Identifier**, enter a name that identifies the file, like **values**.
8. In **Git Fetch Type**, select a branch or commit Id for the manifest, and then enter the Id or branch.
9.  For **Specific Commit ID**, you can also use a [Git commit tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging).
10. In **File Path**, enter the path to the values.yaml file in the repo.
    
    You can enter multiple values file paths by clicking **Add File**. At runtime, Harness will compile the files into one values file.
    
    If you use multiple files, the highest priority is given from the last file, and the lowest priority to the first file. For example, if you have 3 files and the second and third files contain the same `key:value` as the first file, the third file's `key:value` overrides the second and first files.
    
    ![](./static/add-and-override-values-yaml-files-34.png)

1. Click **Submit**. The values file(s) are added to the Service.
   
   ![](./static/add-and-override-values-yaml-files-35.png)
