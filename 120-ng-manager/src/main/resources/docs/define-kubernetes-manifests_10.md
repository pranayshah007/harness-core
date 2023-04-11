# Option: Skip Resource Versioning

By default, Harness versions ConfigMaps and Secrets deployed into Kubernetes clusters. In some cases, you might want to skip versioning.

When you enable **Skip Resource Versioning**, Harness won't perform versioning of ConfigMaps and Secrets for the deployment.

If you've enabled **Skip Resource Versioning** for a few deployments and then disable it, Harness will start versioning ConfigMaps and Secrets.
