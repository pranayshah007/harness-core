## Managed Workloads Table

In Harness, a **managed** Kubernetes workload is a Kubernetes object deployed and managed to steady state. If steady state is not reached, the deployment is considered a failure and the Failure Strategy is executed (typically rollback).



|  | **Apply** | **Rolling** | **Rollback** | **Blue Green** | **Canary** | **Scale** |
| --- | --- | --- | --- | --- | --- | --- |
| **Deployment** | Yes | Yes | Yes | Yes:<br/>1 Deployment or StatefulSet mandatory/allowed | Yes:<br/>1 Deployment or StatefulSet mandatory/allowed | Yes |
| **StatefulSet** | Yes | Yes | Yes | Yes:<br/>1 Deployment or StatefulSet mandatory/allowed | Yes:<br/>1 Deployment or StatefulSet mandatory/allowed | Yes |
| **DaemonSet** | Yes | Yes | Yes | No | No | Yes |
| **CRDs** | Yes | Yes | Yes | No | No | No |
| **Any Object** | Yes | No | No | No | No | No |
