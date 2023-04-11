## Mapping additional Argo CD projects to Harness Projects

When you install a Harness GitOps Agent, Harness can import your existing Argo CD entities into Harness GitOps. We call this Bring Your Own Argo CD (BYOA).

In addition, when you install the Harness GitOps Agent in your existing Argo CD cluster, you can map Argo CD projects to Harness Projects. Harness will import all the Argo CD project entities (applications, clusters, repos, etc) and create them in Harness automatically.

Also, whenever new entities are created in mapped Argo CD projects, they are added to Harness automatically.

For steps on setting up the mapping and import, go to [Map Argo projects to Harness GitOps Projects](multiple-argo-to-single-harness.md).
