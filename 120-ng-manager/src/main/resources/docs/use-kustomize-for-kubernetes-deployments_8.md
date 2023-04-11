## Skip Versioning for Service

In Manifest Details, in Advanced, you can select **Skip Versioning for Service**.

By default, Harness versions ConfigMaps and Secrets are deployed into Kubernetes clusters. Harness uses a ConfigMap for release versioning.

In some cases, you might want to skip versioning.

When you enable **Skip Resource Versioning**, Harness will not perform versioning of ConfigMaps and Secrets for the deployment.

If you have enabled **Skip Resource Versioning** for a few deployments and then disable it, Harness will start versioning ConfigMaps and Secrets.
