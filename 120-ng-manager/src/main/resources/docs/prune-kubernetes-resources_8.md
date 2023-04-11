# Skip pruning for a resource

To ensure that a resource is not pruned, add the annotation `harness.io/skipPruning: "true"`.

When Harness identifies resources from the last successful release which are not in current release, it searches for the `harness.io/skipPruning` annotation and ignores any resources that have it set to `true`.

You can deploy a resource using the annotation `harness.io/skipPruning: "true"`, and then if the manifest is removed and another deployment occurs, Harness will see the annotation `harness.io/skipPruning: "true"` on the resource previously deployed and skip pruning it.

As mentioned in **Limitations** above, you cannot add a resource with the annotation outside of a Harness deployment and have Harness skip the pruning of that resource.
