## Using a Delegate Outside the Cluster

Harness supports OpenShift using a Delegate running externally to the Kubernetes cluster.

Harness does support running Delegates internally for OpenShift 3.11 or greater, but the cluster must be configured to allow images to run as root inside the container in order to write to the filesystem.

Typically, OpenShift is supported through an external Delegate installation. The Delegate is installed outside of the target Kubernetes cluster. Next, you create a Kubernetes Cluster Connector, use its **Master URL** and **Service Account Token** settings, and select the external Delegate.

The following script is a quick method for obtaining the service account token. Run this script wherever you run kubectl to access the cluster.

Set the `SERVICE_ACCOUNT_NAME` and `NAMESPACE` values to the values in your infrastructure.


```bash
SERVICE_ACCOUNT_NAME=default  
NAMESPACE=mynamepace  
SECRET_NAME=$(kubectl get sa "${SERVICE_ACCOUNT_NAME}" --namespace "${NAMESPACE}" -o json | jq -r '.secrets[].name')  
TOKEN=$(kubectl get secret "${SECRET_NAME}" --namespace "${NAMESPACE}" -o json | jq -r '.data["token"]' | base64 -D)  
echo $TOKEN
```

Once configured, OpenShift is used by Harness as a typical Kubernetes cluster.
