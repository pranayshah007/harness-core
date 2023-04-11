# Review: Harness Kubernetes pruning criteria

Kubernetes pruning in Harness is similar to the `kubectl apply --prune` method provided by [Kubernetes](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/declarative-config/#alternative-kubectl-apply-f-directory-prune-l-your-label).

Kubernetes pruning queries the API server for all objects matching a set of labels and attempts to match the returned live object configurations against the object configuration files.

Similarly, Harness compares the objects you are deploying with the objects it finds in the cluster. If Harness finds objects which are not in the current release, it prunes them.

Harness also allows you to identify resources you do not want pruned using the annotation `harness.io/skipPruning`. This is described later in this topic.
