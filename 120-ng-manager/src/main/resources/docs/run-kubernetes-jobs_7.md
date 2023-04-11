# Step 4: Deploy the Job

In your Pipeline, click **Save**, **Run**, and then **Run Pipeline**.

Let's look at the output of the execution.

In the execution, click the Apply step, and then click **Console View**.

In **Fetch Files**, you'll see that Harness successfully fetched the job.yaml:


```
...  
Successfully fetched following files:  
- configmaps/configmap.yaml  
- service.yaml  
- jobs/job.yaml  
- namespace.yaml  
- deployment.yaml  
...
```

In **Initialize**, you can see Harness render the manifest and perform a dry run:


```
Initializing..  
Release Name: [release-b6f492c6530a10cd8fd1792890fc42e05641051b]  
Found following files to be applied in the state  
- jobs/job.yaml  
  
Rendering manifest files using go template  
Only manifest files with [.yaml] or [.yml] extension will be processed  
  
Manifests [Post template rendering] :  
---  
apiVersion: batch/v1  
kind: Job  
metadata:  
  name: pi  
spec:  
  template:  
    spec:  
      containers:  
      - name: pi  
        image: perl  
        command: ["perl",  "-Mbignum=bpi", "-wle", "print bpi(2000)"]  
      restartPolicy: Never  
  backoffLimit: 4  
  
Validating manifests with Dry Run  
kubectl --kubeconfig=config apply --filename=manifests-dry-run.yaml --dry-run  
job.batch/pi created (dry run)  
  
Done.
```

In **Prepare**, you can see Harness process the manifest before applying it:


```
Manifests processed. Found following resources:   
  
Kind                Name                                    Versioned   
Job                 pi                                      false  
```

In **Apply**, you can see the `kubectl apply`:


```
kubectl --kubeconfig=config apply --filename=manifests.yaml --record  
job.batch/pi created  
  
Done.
```

In **Wait for Steady State**, you can see Harness wait for the Job to reach steady state. In Harness, this is a [managed workload](../../cd-technical-reference/cd-k8s-ref/what-can-i-deploy-in-kubernetes.md) because Harness verifies it has reached steady state and fails the Pipeline if it does not.


```
kubectl --kubeconfig=config get events --namespace=default --output=custom-columns=KIND:involvedObject.kind,NAME:.involvedObject.name,NAMESPACE:.involvedObject.namespace,MESSAGE:.message,REASON:.reason --watch-only  
kubectl --kubeconfig=config get jobs pi --namespace=default --output=jsonpath='{.status}'  
  
Status : pi   'map[]'  
Event  : pi   Job    pi     default     Created pod: pi-xfshh   SuccessfulCreate  
Event  : pi   Pod   pi-xfshh   default   Successfully assigned default/pi-xfshh to gke-doc-account-default-pool-d910b20f-25cj   Scheduled  
Event  : pi   Pod   pi-xfshh   default   Pulling image "perl"   Pulling  
  
Status : pi   'map[startTime:2021-09-30T22:51:33Z active:1]'  
  
Status : pi   'map[startTime:2021-09-30T22:51:33Z active:1]'  
Event  : pi   Pod   pi-xfshh   default   Successfully pulled image "perl" in 12.344900106s   Pulled  
  
Status : pi   'map[active:1 startTime:2021-09-30T22:51:33Z]'  
  
Status : pi   'map[startTime:2021-09-30T22:51:33Z active:1]'  
Event  : pi   Pod   pi-xfshh   default   Created container pi   Created  
Event  : pi   Pod   pi-xfshh   default   Started container pi   Started  
  
Status : pi   'map[startTime:2021-09-30T22:51:33Z active:1]'  
Event  : pi   Job   pi    default   Job completed   Completed  
  
Status : pi   'map[conditions:[map[type:Complete status:True lastProbeTime:2021-09-30T22:52:04Z lastTransitionTime:2021-09-30T22:52:04Z]] startTime:2021-09-30T22:51:33Z completionTime:2021-09-30T22:52:04Z succeeded:1]'
```
