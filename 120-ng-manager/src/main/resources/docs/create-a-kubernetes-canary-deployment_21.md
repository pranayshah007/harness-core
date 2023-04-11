### Wrap Up

Wrap Up is long and uses a kubectl describe command to provide information on all containers and pods deployed:

```
kubectl --kubeconfig=config get events --namespace=default --output=custom-columns=KIND:involvedObject.kind,NAME:.involvedObject.name,NAMESPACE:.involvedObject.namespace,MESSAGE:.message,REASON:.reason --watch-only
```

Here is a sample from the output that displays the Kubernetes RollingUpdate:

```
kubectl --kubeconfig=config rollout status Deployment/my-nginx --namespace=default --watch=true  
  
Status : my-nginx deployment "my-nginx" successfully rolled out
```

As you look through the description in **Wrap Up** you can see label added:

```
add label: harness.io/track=stable 
```

You can use the `harness.io/track=stable` label with the values `canary` or `stable` as a selector for managing traffic to these pods, or for testing the pods. For more information, see  [Kubernetes Releases and Versioning](../../cd-technical-reference/cd-k8s-ref/kubernetes-releases-and-versioning.md).

The stage is deployed.

Now that you have successfully deployed your artifact to your Kubernetes cluster pods using your Harness Pipeline, look at the completed workload in the deployment environment of your Kubernetes cluster.

Or you can simply connect to your cluster in a terminal and see the pod(s) deployed:

```
john_doe@cloudshell:~ (project-15454)$ kubectl get pods  
NAME                                                        READY     STATUS    RESTARTS   AGE  
my-nginx-7df7559456-xdwg5                 1/1       Running   0          9h
```
