### Create Variable for JSON key:value

Next, we'll add a Variable for the JSON key:value we will be updating.

1. In **Advanced**, in **Variables**, click **New Variable Override**.
2. In **Variable Name**, enter **asset\_id** and click **Save**.  
   
   The `asset_id` name is a key:value in the config.json files for both dev and prod:
   
   ![](./static/harness-git-ops-application-set-tutorial-45.png)

1. For the Variable Value, select **Runtime Input**:
   
   ![](./static/harness-git-ops-application-set-tutorial-46.png)
   
   Later, when you run the Pipeline, you'll provide a new value for this variable, and that value will be used to update the config.json file.
