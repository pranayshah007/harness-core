# Skip a specific workload

By default, when you run a CD pipeline, Harness will use all of the manifests in the **Manifests** section, and deploy all of its workloads.

To avoid having a specific workload deployed as part of the standard deployment, you add the Harness comment  `# harness.io/skip-file-for-deploy` to the **top** of the file.

This comment instructs Harness to ignore this manifest. Later, you will use the **Apply Step** to deploy this manifest.

For example, here is a ConfigMap file using the `# harness.io/skip-file-for-deploy` comment:


```yaml