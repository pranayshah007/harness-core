# Step 1: Add a Harness GitOps Agent

A Harness GitOps Agent is a worker process that runs in your environment and performs GitOps tasks.

You need to set up an Agent before you can set up a Cluster, Repository, or Application, as the Agent is selected in all three of these.

Typically, you install the Agent in the target cluster, but you can install it any cluster and it can connect to remote clusters using the credentials you provide.

Ensure your Harness Project has the **Continuous Delivery** module enabled.

![](./static/harness-cd-git-ops-quickstart-01.png)

In your Harness Project, click **GitOps**.

The Harness GitOps **Overview**, **Applications**, and **Settings** appear. If this is the first time GitOps has been set up in the Project, the Applications will be empty.

![](./static/harness-cd-git-ops-quickstart-02.png)

All entities other than Applications are in **Settings**.

Click **Settings**. The Harness GitOps settings appear.

![](./static/harness-cd-git-ops-quickstart-03.png)

Click **GitOps** **Agents**.

Click **New GitOps Agent**. The Agent wizard appears.

![](./static/harness-cd-git-ops-quickstart-04.png)

In **Name**, enter the name **example**.

In **Namespace**, enter the namespace where you want to install the Harness GitOps Agent. Typically, this is the target namespace for your deployment. For this quickstart, we use **default**.

Click **Next**. The **Review YAML** settings appear.

This is the manifest YAML for the Harness GitOps Agent. You will download this YAML file and run it in your Harness GitOps Agent cluster.

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
In this output example you can see all of the Harness GitOps objects created in Kubernetes:


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

![](./static/harness-cd-git-ops-quickstart-05.png)

Click **Finish**.

The **Agents** list shows the new Agent as **Healthy** and **Connected**.

![](./static/harness-cd-git-ops-quickstart-06.png)

In your cloud platform Kubernetes cluster you can see the agent workload:

![](./static/harness-cd-git-ops-quickstart-07.png)

Now that you have the Harness GitOps Agent installed, running, and registered, you can configure the remaining components.
