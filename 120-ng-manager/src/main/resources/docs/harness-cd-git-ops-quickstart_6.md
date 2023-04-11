# Step 3: Add a Harness GitOps Cluster

Clusters are the target deployment environments that are synced with the source manifests you add as GitOps Repositories.

In the Cluster setup, you will select the Harness GitOps Agent to use when synching state.

You will also provide the credentials to use when connecting to the target cluster. In this quickstart, we'll connect using the cluster master URL and a Service Account Token for the **default** namespace.

1. In your Harness Project, click **GitOps**, and then click **Settings**.
2. Click **Clusters**, and then click **New Cluster**.
3. In **Name**, enter **example**.
4. In **GitOps Agent**, select the Agent you added earlier in this quickstart, and then click **Continue**.
   
   ![](./static/harness-cd-git-ops-quickstart-11.png)

5. In **Details**, click **Use the credentials of a specific Harness GitOps Agent**. This Cluster will use the Agent's Kubernetes role permissions for connections.
6. Using Specify Kubernetes Cluster URL and credentialsIf you want to use **Specify Kubernetes Cluster URL and credentials**, do the following:
7. In **Master URL**, enter the master URL for your cluster. You can just log into your cluster and run `kubectl cluster-info`. Use the URL listed in the output `Kubernetes master is running at`.
8. In **Authentication**, select **Service Account**.
9.  In **Service Account Token**, paste in the Service Account Token for the cluster's **default** namespace.

To use a Kubernetes Service Account (SA) and token, you will need to either use an existing SA that has `cluster-admin` or *admin* permissions in the namespace, or create a new SA and grant it the permissions. This is described in [Add a Kubernetes Cluster Connector](../../platform/7_Connectors/add-a-kubernetes-cluster-connector.md).

Here's an example of a SA and ClusterRoleBinding with `cluster-admin`:

```yaml
apiVersion: rbac.authorization.k8s.io/v1beta1  
kind: ServiceAccount  
metadata:  
  name: harness-service-account  
  namespace: default  
---  
apiVersion: rbac.authorization.k8s.io/v1beta1  
kind: ClusterRoleBinding  
metadata:  
  name: harness-admin  
roleRef:  
  apiGroup: rbac.authorization.k8s.io  
  kind: ClusterRole  
  name: cluster-admin  
subjects:  
- kind: ServiceAccount  
  name: harness-service-account  
  namespace: default
```
To get a list of the SAs, run `kubectl get serviceAccounts`.

Once you have the SA, use the following commands to get its token (replace `{SA name}` with the Service Account name and `{target namespace}` with the target namespace name, such as **default**):

```bash
SERVICE_ACCOUNT_NAME={SA name}  
  
NAMESPACE={target namespace}  
  
SECRET_NAME=$(kubectl get sa "${SERVICE_ACCOUNT_NAME}" --namespace "${NAMESPACE}" -o=jsonpath='{.secrets[].name}')  
  
TOKEN=$(kubectl get secret "${SECRET_NAME}" --namespace "${NAMESPACE}" -o=jsonpath='{.data.token}' | base64 -d)  
  
echo $TOKEN
```

The token output is decoded and ready to be pasted into Harness.

1. In **Namespace**, enter **default**.
2. Click **Save & Continue**.

Harness validates the connection to the cluster from the Harness GitOps Agent.

![](./static/harness-cd-git-ops-quickstart-12.png)

In this quickstart, the Agent is running inside the target cluster, but you might use an Agent outside a target cluster in your own scenarios. So long as the Agent you select can connect to the target cluster's master URL, you can add the cluster in Harness GitOps.

Now that you have a Harness GitOps Agent, Repository, and Cluster set up, you're ready to add a Harness GitOps Application.
