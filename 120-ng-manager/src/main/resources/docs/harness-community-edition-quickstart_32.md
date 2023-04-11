### GCP GKE

See the GCP doc [Install kubectl and configure cluster access](https://cloud.google.com/kubernetes-engine/docs/how-to/cluster-access-for-kubectl) for a good overview.

Basically, you just need to install the gcloud CLI as described in [Installing the gcloud CLI](https://cloud.google.com/sdk/docs/install) from GCP.

Once gcloud is installed you can connect to the remote cluster.

The command will look something like this.

```
gcloud container clusters get-credentials [cluster-name] --zone [zone-name] --project [project-name]
```

Once you're connected, you simply run the command to install the Delegate:

```
kubectl apply -f harness-delegate.yaml
```
