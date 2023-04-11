# Step 2: Install the Agent

Click **Download & Continue**. You are prompted to save the YAML file.

Open a terminal and navigate to the folder where you downloaded the YAML file.

In the same terminal, log into the Kubernetes cluster where you want to install the Agent.

For example, here's a typical GKE login:


```
gcloud container clusters get-credentials <cluster_name> --zone us-central1-c --project <project_name>
```

Run the following command to apply the YAML file you downloaded (in this example, `default` was the namespace entered in the **Namespace** setting):

```
kubectl apply -f gitops-agent.yaml -n default
```

In the following output example you can see all of the Harness GitOps objects created in Kubernetes.

This example output is for installing a new Harness GitOps Agent without using an existing Argo CD instance.

```
% kubectl apply -f harness-gitops-agent.yaml -n default  
customresourcedefinition.apiextensions.k8s.io/applications.argoproj.io created  
customresourcedefinition.apiextensions.k8s.io/appprojects.argoproj.io created  
serviceaccount/argocd-application-controller created  
serviceaccount/argocd-redis created  
serviceaccount/example-agent created  
role.rbac.authorization.k8s.io/example-agent created  
role.rbac.authorization.k8s.io/argocd-application-controller created  
clusterrole.rbac.authorization.k8s.io/argocd-application-controller-default created  
clusterrole.rbac.authorization.k8s.io/example-agent created  
rolebinding.rbac.authorization.k8s.io/argocd-application-controller created  
rolebinding.rbac.authorization.k8s.io/argocd-redis created  
clusterrolebinding.rbac.authorization.k8s.io/argocd-application-controller-default created  
rolebinding.rbac.authorization.k8s.io/example-agent created  
clusterrolebinding.rbac.authorization.k8s.io/example-agent created  
configmap/argocd-cm created  
configmap/argocd-cmd-params-cm created  
configmap/argocd-gpg-keys-cm created  
configmap/argocd-rbac-cm created  
configmap/argocd-ssh-known-hosts-cm created  
configmap/argocd-tls-certs-cm created  
secret/argocd-secret created  
service/argocd-metrics created  
service/argocd-redis created  
service/argocd-repo-server created  
deployment.apps/argocd-redis created  
deployment.apps/argocd-repo-server created  
statefulset.apps/argocd-application-controller created  
networkpolicy.networking.k8s.io/argocd-application-controller-network-policy created  
networkpolicy.networking.k8s.io/argocd-redis-network-policy created  
networkpolicy.networking.k8s.io/argocd-repo-server-network-policy created  
secret/example-agent created  
configmap/example-agent created  
deployment.apps/example-agent created  
configmap/example-agent-upgrader created  
role.rbac.authorization.k8s.io/example-agent-upgrader created  
rolebinding.rbac.authorization.k8s.io/example-agent-upgrader created  
serviceaccount/example-agent-upgrader created  
Warning: batch/v1beta1 CronJob is deprecated in v1.21+, unavailable in v1.25+; use batch/v1 CronJob  
cronjob.batch/example-agent-upgrader created
```

Back in Harness, click **Continue**.

Harness indicates that the Harness GitOps Agents is registered.

![](./static/install-a-harness-git-ops-agent-90.png)

Click **Continue**.

:::note

**Mapping Argo CD projects to Harness Projects:** See [Map Argo projects to Harness GitOps Projects](multiple-argo-to-single-harness.md).

:::

When you are finished, the **Agents** list shows the new Agent as **Healthy** and **Connected**.

![](./static/install-a-harness-git-ops-agent-91.png)

In your cloud platform Kubernetes cluster you can see the agent workload:

![](./static/install-a-harness-git-ops-agent-92.png)

Now that you have the Harness GitOps Agent installed, running, and registered, you can configure the remaining components.
