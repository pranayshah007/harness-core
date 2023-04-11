# Add the Fetch Instance Script

In **Fetch Instance Script**, you enter the script to pull the JSON collection of target instances from a server or service.

The script is expected to query the server and receive a JSON array containing the target hosts, saved in the file `$INSTANCE_OUTPUT_PATH`.

This shell script will be executed at runtime by the Harness Delegate on its host. This should be a shell script you have run on the Delegate host to ensure that the host can connect to your server(s).

The script should return a JSON array containing the target host information Harness needs to deploy.

The script you use depends on the platform you're using.

For this tutorial, we want to deploy to all the Kubernetes pods in the namespace example, so our script looks like this:

```bash
/opt/harness-delegate/client-tools/kubectl/v1.19.2/kubectl get pods --namespace=harness-delegate-ng -o json > $INSTANCE_OUTPUT_PATH
```

If you want, you can replace the namespace `harness-delegate-ng` with a namespace in your own cluster that has pods that can be fetched.

**Important:** The `$INSTANCE_OUTPUT_PATH` expression must come last. You can enter the script inline, or use the Harness File Store to store it in a file.

This script returns a JSON array of pods in the namespace, similar to this:


```json
{  
    "apiVersion": "v1",  
    "items": [  
        {  
            "apiVersion": "v1",  
            "kind": "Pod",  
            "metadata": {  
                "creationTimestamp": "2022-09-28T20:13:50Z",  
                "generateName": "example-deployment-69877c747f-",  
                "labels": {  
                    "pod-template-hash": "69877c747f"  
                },  
                "name": "example-deployment-69877c747f-gllvt",  
                "namespace": "harness-delegate-ng",  
...  
        {  
            "apiVersion": "v1",  
            "kind": "Pod",  
            "metadata": {  
                "creationTimestamp": "2022-09-28T20:13:50Z",  
                "generateName": "example-deployment-69877c747f-",  
                "labels": {  
                    "pod-template-hash": "69877c747f"  
                },  
                "name": "example-deployment-69877c747f-gpkkk",  
                "namespace": "harness-delegate-ng",  
...  
}
```

This example uses Kubernetes. Harness already has full, first-class support for Kubernetes deployments. We just use this script as an example. 

For the main Kubernetes support, go to [Kubernetes deployment tutorial](kubernetes-cd-quickstart).

Next, you need to provide the JSON path to the JSON array object for the target hosts.
