## Configuration File Repository

**Configuration File Repository** is where you add a connection to the Terragrunt script repo hosting the scripts and files for this step.

The **Configuration File Repository** setting is available in the Terragrunt Plan step. It is available in the Terragrunt Apply and Destroy steps when **Inline** is selected in **Configuration Type**. 

1. Click **Specify Config File** or click the edit icon. The **Terragrunt Config File Store** settings appear.
2. Click the provider where your files are hosted.
    
    ![picture 4](static/2c7889d9dbae6966e8899d90310b0564b4552af33f2fffb553d30d11d96298d7.png)
3. Select or create a [Git connector](https://developer.harness.io/docs/platform/Connectors/connect-to-code-repo) for your repo.
4. Once you have selected a connector, click **Continue**.
   
   In **Config File Details**, provide the Git repo details.
5. In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit Id**.
   
   When you run the Pipeline, Harness will fetch the script from the repo.
   
   **Specific Commit Id** also supports Git tags. If you think the script might change often, you might want to use **Specific Commit Id**. For example, if you are going to be fetching the script multiple times in your pipeline, Harness will fetch the script each time. If you select **Latest from Branch** and the branch changes between fetches, different scripts are run.
6. In **Branch**, enter the name of the branch to use.
7. In **File Path**, enter the path from the root of the repo to the file containing the script.
8. Click **Submit**.
