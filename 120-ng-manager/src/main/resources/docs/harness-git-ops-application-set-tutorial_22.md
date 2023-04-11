# Run and verify the PR Pipeline

Now your PR Pipeline is ready.

1. Click **Save**, and then click **Run**.
2. In **Run Pipeline**, in **Specify Environment**, select the **dev** Environment.
3. In **Environment Variables**, for **asset\_id**, enter the value `12345678`.
4. In **Specify GitOps Clusters**, select the **engineeringdev** cluster.
   
   ![](./static/harness-git-ops-application-set-tutorial-57.png)

5. Click **Run Pipeline**.

  You can review the deployment steps in real-time.

  ![](./static/harness-git-ops-application-set-tutorial-58.png)

  Here's an example of each step:

* Service:
  ```bash
  Starting service step...  
  Processing service variables...  
  Applying environment variables and service overrides  
  Processed service variables  
  Processed artifacts and manifests  
  Completed service step
  ```

* GitOps Clusters:
  ```bash
  Environment(s): {dev}   
    
  Processing clusters at scope PROJECT  
  Following 1 cluster(s) are present in Harness Gitops  
  Identifiers: {engineeringdev}   
    
  Following 1 cluster(s) are selected after filtering  
  Identifiers: {engineeringdev}   
    
  Completed
  ```

* Update Release Repo:
  
  ![](./static/harness-git-ops-application-set-tutorial-59.png)

* Merge PR:
  ```bash
  PR Link: https://github.com/michaelcretzman/applicationset/pull/5  
  Pull Request successfully merged  
  Commit Sha is 36f99ff737b98986045365e1b2be1326e97d4836  
  Done.
  ```

6. Check the repo to see that the config.json file for the dev environment has been updated with the new **asset\_id** value:

  ![](./static/harness-git-ops-application-set-tutorial-60.png)

Congratulations! You PR Pipeline was successful.

In this tutorial, you learned how to:

1. Create an ApplicationSet that defines one application and syncs it to multiple target environments.
2. Create a Harness PR Pipeline to change the application in just one of the target environments.
