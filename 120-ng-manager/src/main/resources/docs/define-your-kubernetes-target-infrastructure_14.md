# Release Name

Harness requires a Kubernetes release name for tracking.

The release name must be unique across the cluster.

See [Kubernetes Releases and Versioning](../../cd-technical-reference/cd-k8s-ref/kubernetes-releases-and-versioning.md).

The Harness-generated unique identifier `<+infrastructure.infrastructureKey>` can be used to ensure a unique release name.

Use `release-<+infrastructure.infrastructureKey>` for the **Release Name** instead of just `<+infrastructure.infrastructureKey>`. Kubernetes service and pod names follow DNS-1035 and must consist of lowercase alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character. Using `release-` as a prefix will prevent any issues.
