# Review: Deployment Strategy Support

In addition to standard workload type support in Harness (see [What Can I Deploy in Kubernetes?](what-can-i-deploy-in-kubernetes.md)), Harness supports [DeploymentConfig](https://docs.openshift.com/container-platform/4.1/applications/deployments/what-deployments-are.html), [Route](https://docs.openshift.com/enterprise/3.0/architecture/core_concepts/routes.html), and [ImageStream](https://docs.openshift.com/enterprise/3.2/architecture/core_concepts/builds_and_image_streams.html#image-streams) across Canary, Blue Green, and Rolling deployment strategies.

Use `apiVersion: apps.openshift.io/v1` and not `apiVersion: v1`.
