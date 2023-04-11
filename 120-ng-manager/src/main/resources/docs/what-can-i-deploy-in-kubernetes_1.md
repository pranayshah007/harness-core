# Managed and Unmanaged Workloads

In Harness, a **managed** Kubernetes workload is a Kubernetes object deployed and managed to steady state. If steady state is not reached, the deployment is considered a failure and the Failure Strategy is executed (typically rollback).

An unmanaged workload is a workload deployed separate from your primary workload, such as [Kubernetes Jobs](../../cd-execution/kubernetes-executions/run-kubernetes-jobs.md). Harness does not track these workload versions or perform rollback on them.
