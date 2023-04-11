## Automatically added Argo CD repositories

When adding Argo CD repositories, Harness automatically generates the name of Repository when it's added to Harness. This is necessary because Argo CD has no name setting for its repos.

The process for generating the name is: take the repo name, remove any dashes, and then add an underscore and a unique suffix.

For example, the Argo CD repo `https://github.com/argoproj/gitops-engine.git` is named `gitopsengine_kmjzyrbs` in Harness

![](./static/multiple-argo-to-single-harness-76.png)
