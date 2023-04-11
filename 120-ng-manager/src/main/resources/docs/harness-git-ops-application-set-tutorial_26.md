## PR Pipelines

Often, even though your ApplicationSet syncs one microservice/application to multiple target environments, you might want to change a microservice in just one of the target environments, such as a dev environment. A Harness PR Pipeline enables you to do this.

When you deploy a Harness PR Pipeline, you simply indicate what target environment application you want to update and the config.json keys/values you want changed, such as release tags. Harness creates the pull request in your Git repo and merges it for you. Now, the target environment application has the new keys/values.

![](./static/harness-git-ops-application-set-tutorial-62.png)
