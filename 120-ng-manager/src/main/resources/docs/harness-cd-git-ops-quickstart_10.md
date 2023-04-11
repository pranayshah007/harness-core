# Cleaning Up

To delete the Harness GitOps Agent from your Kubernetes cluster, you delete the StatefulSet for the Agent. Once created, the StatefulSet ensures that the desired number of pods are running and available at all times. Deleting the pod without deleting the StatefulSet will result in the pod being recreated.

For example, if you have the Agent pod name `gitops-agent-6877dbf7bf-wg6xv`, you can delete the StatefulSet with the following command:

`$ kubectl delete statefulset -n default gitops-agent-6877dbf7bf`

You can also simply delete and recreate the namespace which will delete all resources except for RoleBindings, ServiceAccounts or NetworkPolicy:


```bash
kubectl delete namespace {namespace}  
  
kubectl create namespace {namespace}
```
