# What Kubernetes workloads can I include?

The **Apply Step** can deploy all workload types, including Jobs.

All workloads deployed by the Apply step are managed workloads. Managed workloads are tracked until steady state is reached.

The Apply Step is primarily used for deploying Jobs controllers, but it can be used for other resources. Typically, when you want to deploy multiple workloads (Deployment, StatefulSet, and DaemonSet) you will use separate stages for each.Other Kubernetes steps, such as Rolling, are limited to specific workload types.

For a detailed list of what Kubernetes workloads you can deploy in Harness, see [What Can I Deploy in Kubernetes?](../../cd-technical-reference/cd-k8s-ref/what-can-i-deploy-in-kubernetes.md).
