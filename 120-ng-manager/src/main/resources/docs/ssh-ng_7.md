# Set up Artifact Location and Details

For this tutorial, we'll use a **ToDo List** app artifact, **todolist.war**, available in a public Harness Artifactory repo.

1. In **Artifact Details**, enter the following:
	1. In **Artifact Source Name**, enter **Todolist**.
	2. In **Repository Format**, keep the default value **Generic**.
	3. For **Repository**, enter: **todolist-tutorial**. Note that if you select the down-drop menu for Repository, Harness loads any available repositories and displays them for selection.
	4. In **Artifact Directory**, enter a forward slash **/**.
	5. In **Artifact Details**, keep the default **Value**.
	6. In **Artifact Path**, leave the default Runtime Input value **<+input>** for that field. Click **Submit.**
   
   ![](./static/ssh-ng-172.png)
   
   The artifact is added to your Service.
   
   ![](./static/ssh-ng-173.png)

7. Click **Save**. The Service is added to your stage.
2. Click **Continue** to set up the target Environment.
