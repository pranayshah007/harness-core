## Blue Green deployments

When the **Enable Kubernetes Pruning** setting is enabled, Kubernetes Blue Green deployments manage pruning as follows:

1. In the first step of a Blue Green deployment, the new version of the release is deployed to the stage environment (pod set).
2. Harness prunes by comparing the new and previous releases in the stage pod set. Harness prunes the resources from the last successful release which are not in the current release.

Let's look at an example.

1. Deployment 1 is successfully deployed. It contained manifests for resources a, b, and c.
2. Deployment 2 failed. It contained manifests for resources a, c, and d, but not b.
3. Before failure, resource d is created and resource b is pruned.
4. During rollback, Harness recreates the previously pruned resource b and deletes resource d.
