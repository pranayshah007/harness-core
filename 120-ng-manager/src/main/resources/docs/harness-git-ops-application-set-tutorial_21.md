## Review Execution steps

In **Execution**, Harness automatically adds the following steps:

* **Update Release Repo:** this step will fetch your JSON files, update them with your changes, Commit and Push, and then create the PR.
  You can also enter variables in this step to update key:value pairs in the config file you are deploying.  

  If there is a matching variable name in the variables of the Service or Environment used in this Pipeline, the variable entered in this step will override them.  
  
  ![](./static/harness-git-ops-application-set-tutorial-56.png)

* **Merge PR:** merges the new PR.

You don't have to edit anything in these steps.
