# Scaling and Rollback

Each time Harness deploys, the deployment gets new replicas that get scaled up and the old replicas are scaled down.

By default, Kubernetes keeps the last 10 revisions as [ReplicaSets](https://kubernetes.io/docs/concepts/workloads/controllers/replicaset/).

When Harness performs rollback it identifies which revision number to use (`rollback-to`). Harness identifies that last successful ReplicaSet, and selects it to be scaled up.
