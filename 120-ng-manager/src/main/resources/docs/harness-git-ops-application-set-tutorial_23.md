# ApplicationSet and PR Pipeline Summary

A typical GitOps Application syncs a source manifest to a destination cluster. If you have multiple target clusters, you could create separate GitOps Applications for each one, but that makes management more challenging. Also, what if you want to sync an application with 100s of target clusters? Managing 100s of GitOps Applications is not acceptable.

To solve this use case, Harness supports ApplicationSets.
