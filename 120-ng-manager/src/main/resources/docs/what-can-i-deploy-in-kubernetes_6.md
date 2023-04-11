# Deploy Unmanaged Workloads using Annotation

To deploy an object outside of the managed workloads in any strategy, you use the Harness [annotation](kubernetes-annotations-and-labels.md) to make it unmanaged: `harness.io/direct-apply: "true"|"false"`. Set to `true` to make a manifest an unmanaged workload.

For example, Harness Canary and Blue Green steps support a single **Deployment** or **StatefulSet** workload as a managed entity, but you can deploy additional workloads as unmanaged using the `harness.io/direct-apply` annotation.

The following tables list the differences between the managed and unmanaged workloads for the different Kubernetes steps.
