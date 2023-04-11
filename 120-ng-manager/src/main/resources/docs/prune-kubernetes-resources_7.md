# Review: pruning examples

The first time you deploy a resource (Deployment, StatefulSet, ReplicaSet, etc) no pruning will take place.

In Harness Pipeline execution, you will see a **Prune** section with the following message:


```bash
No previous successful deployment found, so no pruning required
```

When the **Enable Kubernetes Pruning** setting is enabled and Harness finds resources that match the pruning criteria, you will see a message like this:


```bash
kubectl --kubeconfig=config delete Deployment/k8s-orphaned-resource-b --namespace=default  
  
deployment.apps "k8s-orphaned-resource-b" deleted  
  
kubectl --kubeconfig=config delete ConfigMap/k8s-orphaned-resource-configmap-b --namespace=default  
  
configmap "k8s-orphaned-resource-configmap-b" deleted  
  
Pruning step completed
```

If a deployment fails, Harness recreates any of the pruned resources it removed as part of the deployment. In the **Rollback** step, you will see a **Recreate Pruned Resources** section with message like this:


```bash
kubectl --kubeconfig=config apply --filename=manifests.yaml --record  
  
deployment.apps/k8s-orphaned-resource-f created  
  
Successfully recreated pruned resources.
```
