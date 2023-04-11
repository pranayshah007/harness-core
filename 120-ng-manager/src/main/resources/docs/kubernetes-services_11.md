### Deployment strategy support

In addition to standard workload type support in Harness (see [What can I deploy in Kubernetes?](https://developer.harness.io/docs/continuous-delivery/cd-technical-reference/cd-k8s-ref/what-can-i-deploy-in-kubernetes)), Harness supports [DeploymentConfig](https://docs.openshift.com/container-platform/4.1/applications/deployments/what-deployments-are.html), [Route](https://docs.openshift.com/enterprise/3.0/architecture/core_concepts/routes.html), and [ImageStream](https://docs.openshift.com/enterprise/3.2/architecture/core_concepts/builds_and_image_streams.html#image-streams) across Canary, Blue Green, and Rolling deployment strategies.

Please use `apiVersion: apps.openshift.io/v1` and not `apiVersion: v1`.
