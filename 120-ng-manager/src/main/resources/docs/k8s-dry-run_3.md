# Step output example

When you run the pipeline, the output of the Dry Run step indicates that the manifest was applied as a dry run using `(dry run)`:

```Sh
Validating manifests with Dry Run
kubectl --kubeconfig=config apply --filename=manifests-dry-run.yaml --dry-run
namespace/dev created (dry run)
configmap/nginx-k8s-config created (dry run)
service/nginx-k8s-svc created (dry run)
deployment.apps/nginx-k8s-deployment created (dry run)

Done.
```
