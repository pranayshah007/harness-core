# Important notes

* To prevent pruning using the Harness annotation `harness.io/skipPruning: "true"`, the resource must have been deployed by Harness.
  * Harness pruning does not consider resources outside of a Harness deployment.
  * If you make any changes to your Kubernetes resources using a tool other than Harness (before or after the deployment), Harness does not track those changes.
* The maximum manifest/chart size is 0.5MB. When Harness prunes, it stores the full manifest in configMap to use it as part of release history. While deploying very large manifests/charts though Kubernetes, Harness is limited by configMap capacity.
* While it is unlikely, if you are using the same entity in two Harness Services, Harness does not know this. So if you prune the resource in one deployment it might be unavailable in another deployment. Use the annotation `harness.io/skipPruning: "true"` to avoid issues.
