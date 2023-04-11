## Rolling deployments

When the **Enable Kubernetes Pruning** setting is enabled, Kubernetes Rolling deployments manage pruning as follows:

1. During deployment, Harness compares resources in the last successful release with the current release.
2. Harness prunes the resources from the last successful release that are not in current release.
3. If a deployment fails, Harness recreates the pruned resources during its Rollback stage.
4. During rollback, any new resources that were created in the failed deployment stage that were not in the last successful release are deleted also.
