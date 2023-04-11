### Azure AKS

See the Azure doc [Quickstart: Deploy an Azure Kubernetes Service cluster using the Azure CLI](https://docs.microsoft.com/en-us/azure/aks/kubernetes-walkthrough) for a good overview.

The commands will look something like this.

Install the Azure CLI:

```
az aks install-cli
```

Set the Azure subscription to use:

```
az account set --subscription [subscription-id]
```

Connect to the cluster:

```
az aks get-credentials --resource-group [resource-group-name] --name [cluster-name]
```

Test the connection:

```
kubectl get nodes
```

Next, you simply run the command to install the Delegate:

```
kubectl apply -f harness-delegate.yaml
```
