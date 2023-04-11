# Create a PR Pipeline

As noted earlier, when you deploy a Harness PR Pipeline, you simply indicate the target environment application and the config.json keys/values you want changed. Harness creates the pull request in your Git repo and merges it for you. Once complete, the target environment application has the new keys/values.

![](./static/harness-git-ops-application-set-tutorial-43.png)

For the PR Pipeline, we'll create two Harness Environments, dev and prod. These names are the same as the folder names in the repo:

![](./static/harness-git-ops-application-set-tutorial-44.png)

We use the same names so that when we select a Harness Environment we can pass along the same name as the target folder.

Next, we'll create a Harness Service that points to the config.json files in these directories.

The path to the config.json files in the Service will use the expression <+env.name>: `examples/git-generator-files-discovery/cluster-config/engineering/<+env.name>/config.json`.

At runtime, this expression resolves to the Harness Environment you selected.

When you run the Pipeline, you'll select which Environment to use, dev or prod, and Harness will use the corresponding repo folder and update that application only.
