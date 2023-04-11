# Step 3: Deploy

Each Helm chart deployment is treated as a release. During deployment, when Harness detects that there is a previous release for the chart, it upgrades the chart to the new release.

In your pipeline, click **Run**.

The Helm chart deployment runs.

You will see Harness fetch the Helm chart. Here is an example:


```bash
Helm repository: Bitnami Helm Repo  
  
Chart name: nginx  
  
Chart version: 9.4.1  
  
Helm version: V3  
  
Repo url: https://charts.bitnami.com/bitnami  
  
Successfully fetched values.yaml  
  
Fetching files from helm chart repo  
  
Helm repository: Bitnami Helm Repo  
  
Chart name: nginx  
  
Helm version: V3  
  
Repo url: https://charts.bitnami.com/bitnami  
  
Successfully fetched following files:  
  
- nginx/.helmignore  
- nginx/charts/common/.helmignore  
- nginx/charts/common/templates/validations/_postgresql.tpl  
- nginx/charts/common/templates/validations/_cassandra.tpl  
- nginx/charts/common/templates/validations/_mongodb.tpl  
- nginx/charts/common/templates/validations/_mariadb.tpl  
- nginx/charts/common/templates/validations/_validations.tpl  
- nginx/charts/common/templates/validations/_redis.tpl  
- nginx/charts/common/templates/_ingress.tpl  
- nginx/charts/common/templates/_names.tpl  
- nginx/charts/common/templates/_affinities.tpl  
- nginx/charts/common/templates/_storage.tpl  
- nginx/charts/common/templates/_utils.tpl  
- nginx/charts/common/templates/_errors.tpl  
- nginx/charts/common/templates/_capabilities.tpl  
- nginx/charts/common/templates/_secrets.tpl  
- nginx/charts/common/templates/_warnings.tpl  
- nginx/charts/common/templates/_tplvalues.tpl  
- nginx/charts/common/templates/_images.tpl  
- nginx/charts/common/templates/_labels.tpl  
- nginx/charts/common/Chart.yaml  
- nginx/charts/common/values.yaml  
- nginx/charts/common/README.md  
- nginx/Chart.lock  
- nginx/templates/svc.yaml  
- nginx/templates/health-ingress.yaml  
- nginx/templates/ldap-daemon-secrets.yaml  
- nginx/templates/tls-secrets.yaml  
- nginx/templates/NOTES.txt  
- nginx/templates/pdb.yaml  
- nginx/templates/ingress.yaml  
- nginx/templates/server-block-configmap.yaml  
- nginx/templates/serviceaccount.yaml  
- nginx/templates/hpa.yaml  
- nginx/templates/servicemonitor.yaml  
  
Done.
```

Next, Harness will initialize and prepare the workloads, apply the Kubernetes manifests, and wait for steady state.

In **Wait for Steady State** you will see the workloads deployed and the pods scaled up and running (the release name has been shortened for readability):


```bash
kubectl --kubeconfig=config get events --namespace=default --output=custom-columns=KIND:involvedObject.kind,NAME:.involvedObject.name,NAMESPACE:.involvedObject.namespace,MESSAGE:.message,REASON:.reason --watch-only  
  
kubectl --kubeconfig=config rollout status Deployment/release-e008...ee-nginx --namespace=default --watch=true  
  
Status : release-e008...ee-nginx   Waiting for deployment spec update to be observed...  
  
Event  : release-e008...ee-nginx   Deployment   release-e008...ee-nginx   default     Scaled up replica set release-e008...ee-nginx-779cd786f6 to 1   ScalingReplicaSet  
  
Status : release-e008...ee-nginx   Waiting for deployment spec update to be observed...  
  
Status : release-e008...ee-nginx   Waiting for deployment "release-e008...ee-nginx" rollout to finish: 0 out of   
  
Event  : release-e008...ee-nginx   ReplicaSet   release-e008...ee-nginx-779cd786f6   default   Created pod: release-e008...ee-nginx-779n765l   SuccessfulCreate  
  
Status : release-e008...ee-nginx   Waiting for deployment "release-e008...ee-nginx" rollout to finish: 0 of 1 updated replicas are available...  
  
Event  : release-e008...ee-nginx   Pod   release-e008...ee-nginx-779n765l   default   Successfully assigned default/release-e008...ee-nginx-779n765l to gke-doc-account-default-pool-d910b20f-argz   Scheduled  
  
Event  : release-e008...ee-nginx   Pod   release-e008...ee-nginx-779n765l   default   Pulling image "docker.io/bitnami/nginx:1.21.1-debian-10-r0"   Pulling  
  
Event  : release-e008...ee-nginx   Pod   release-e008...ee-nginx-779n765l   default   Successfully pulled image "docker.io/bitnami/nginx:1.21.1-debian-10-r0" in 3.495150157s   Pulled  
  
Event  : release-e008...ee-nginx   Pod   release-e008...ee-nginx-779n765l   default   Created container nginx   Created  
  
Event  : release-e008...ee-nginx   Pod   release-e008...ee-nginx-779n765l   default   Started container nginx   Started  
  
Status : release-e008...ee-nginx   deployment "release-e008...ee-nginx" successfully rolled out  
  
Done.
```
You deployment is successful.
