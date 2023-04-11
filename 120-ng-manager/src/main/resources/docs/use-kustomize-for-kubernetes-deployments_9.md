# Option: Optimize by Fetching a Subset of the Kustomize Files

When you specify a folder path for your Git repo in **Kustomize Folder Path** within **Manifest Details**, Harness fetches and downloads the complete repo. The amount of time it takes to fetch and download the files from your Git repo depends on the number of files present in your repo. A Git repo with hundreds or thousands of configuration files can take more time when Harness fetches those files.

Instead of fetching the entire Git repo, you can fetch a subset of the Kustomize manifests and configuration files. You can do this by specifying your Git repo folder path for **Kustomize Base Path** and the relative folder path for **Kustomize YAML Folder Path** in **Manifest Details**.

In **Manifest Details**, enter the following required settings:
  * **Manifest Name:** enter the name for this manifest.
  * **Git Fetch Type:** select **Latest from Branch**.
  * **Branch:** enter **main** or **master**.
  * **Kustomize Base Path:** When you select **Optimized Kustomize Manifest Collection**, this field changes from **Kustomize Folder Path** to **Kustomize Base Path**. Enter the folder path for your Git repo inside which all of the Kustomize dependencies and base manifests are present. Harness fetches and downloads this folder instead of the entire Git repo. The folder path shown in the dialog is an example.
  * **Kustomize YAML Folder Path:** enter the relative folder path for your Git repo where the kustomize.yaml file is located. The folder path shown in the dialog is an example.
    
    As an example, if kustomization.yaml is present in this path: **kustomize/multipleEnv/environments/production** and the **kustomize/multipleEnv** folder contains all of the kustomize dependencies, then the folder paths would be as follows:

    * **Kustomize Base Path:** kustomize/multipleEnv/
    * **Kustomize YAML Folder Path:** environments/production/


