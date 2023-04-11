# Mapping Argo CD projects to Harness Projects

The following sections describe how to map Argo CD projects to Harness Projects when installing a new Agent. Mapping Argo CD projects in an existing Agent is covered in [Adding new mappings to existing Agent](#adding_new_mappings_to_existing_agent).

The following steps show you how to install a GitOps Agent into an existing Argo CD namespace and then map your existing projects to your Harness Project.

1. In your Harness account, click **Account Settings**.
2. click **GitOps**, and then click **Agents**.
   
   ![](./static/multiple-argo-to-single-harness-64.png)

3. Click **New GitOps Agent**.
4. In **Agent Installation**, in **Do you have any existing Argo CD instances**, click **Yes**, and then click **Start**.
   
   ![](./static/multiple-argo-to-single-harness-65.png)

5. In **Name**, enter a name for your agent, such as **byoa-agent**.
6. In **Namespace**, enter the namespace where Argo CD is hosted. The default is **argocd**.
   
   ![](./static/multiple-argo-to-single-harness-66.png)

7. Click **Continue**.
8. In **Review YAML**, click **Download & Continue**.
9.  Log into the cluster hosting Argo CD.
10. Run the install command provided in the Agent installer, such as `kubectl apply -f gitops-agent.yml -n argocd`. You'll see output similar to this:
    ```bash
    serviceaccount/byoa-agent-agent created  
    role.rbac.authorization.k8s.io/byoa-agent-agent created  
    clusterrole.rbac.authorization.k8s.io/byoa-agent-agent created  
    rolebinding.rbac.authorization.k8s.io/byoa-agent-agent created  
    clusterrolebinding.rbac.authorization.k8s.io/byoa-agent-agent created  
    secret/byoa-agent-agent created  
    configmap/byoa-agent-agent created  
    deployment.apps/byoa-agent-agent created  
    configmap/byoa-agent-agent-upgrader created  
    role.rbac.authorization.k8s.io/byoa-agent-agent-upgrader created  
    rolebinding.rbac.authorization.k8s.io/byoa-agent-agent-upgrader created  
    serviceaccount/byoa-agent-agent-upgrader created  
    cronjob.batch/byoa-agent-agent-upgrader created
    ```
1.  Back in the Harness GitOps Agent installer, click **Continue**.
   
   The Agent has registered with Harness.
   
   ![](./static/multiple-argo-to-single-harness-67.png)

2.  Click **Continue**. The **Map Projects** settings appear.
