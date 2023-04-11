### Specify manifest details

Now we'll define the manifest to use for the PR Pipeline. We'll use the path to the config.json files. We'll use the expression `<+env.name>` in the path so that we can dynamically select the path based on the Harness Environment we select: **dev** or **prod**.

1. In **Manifest Details**, enter the following settings and then click **Submit**.
	1. **Manifest Name:** enter **config.json**.
	2. **Git Fetch Type:** select **Latest from Branch**.
	3. **Branch:** enter the name of the main branch (master, main, etc).
	4. **File Path:** enter `examples/git-generator-files-discovery/cluster-config/engineering/<+env.name>/config.json`.  
	
  Note the use of `<+env.name>`.
  
  ![](./static/harness-git-ops-application-set-tutorial-53.png)

2. Back in **New Service**, click **Save**.

  The Service is added to the Pipeline.

  ![](./static/harness-git-ops-application-set-tutorial-54.png)

1. Click **Continue** to add the Environment.
