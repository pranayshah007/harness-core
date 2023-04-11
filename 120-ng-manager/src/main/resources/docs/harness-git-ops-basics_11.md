# Storage

All GitOps information is stored on your cluster as ConfigMaps and Secrets. Essentially, the cluster acts as the database for GitOps.

Your Harness GitOps Application, Repository, Cluster configurations, etc, are all stored on the PersistentVolume of the cluster hosting the Agent.

Harness SaaS is used to store the state cache only.
