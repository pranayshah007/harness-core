# Review: what workloads can I deploy?

Stages using Harness Canary and Blue/Green steps only support [Kubernetes Deployment workloads](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/).

The Rolling Deployment step supports all workloads except Jobs.

The [Apply Step](deploy-manifests-using-apply-step.md) can deploy any workloads or objects in any strategy including Rolling Deployment.

In Harness, a workload is a Deployment, StatefulSet, or DaemonSet object deployed and managed to steady state.
