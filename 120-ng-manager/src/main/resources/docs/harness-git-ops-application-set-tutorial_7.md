# Clone and update the ApplicationSet repo

The repo and example we will use is located in the Argo Project's public [ApplicationSet repo](https://github.com/argoproj/applicationset):

`https://github.com/argoproj/applicationset/tree/master/examples/git-generator-files-discovery`

For a summary of this example, go to [Argo CD docs](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Git/#git-generator-files). You will need to clone this repo into your GitHub account and then update 3 files.

1. Clone the repo `https://github.com/argoproj/applicationset`.
2. Navigate to `applicationset/examples/git-generator-files-discovery/git-generator-files.yaml`.
3. Update `git-generator-files.yaml` with the following YAML keys/values:


```yaml
apiVersion: argoproj.io/v1alpha1  
kind: ApplicationSet  
metadata:  
  name: guestbook  
spec:  
  generators:  
    - git:  
        repoURL: https://github.com/<your account name>/applicationset.git  
        revision: HEAD  
        files:  
        - path: "examples/git-generator-files-discovery/cluster-config/**/config.json"  
  template:  
    metadata:  
      name: '{{cluster.name}}-guestbook'  
    spec:  
      project: <Harness GitOps Agent Project Id>  
      source:  
        repoURL: https://github.com/<your account name>/applicationset.git  
        targetRevision: HEAD  
        path: "examples/git-generator-files-discovery/apps/guestbook"  
      destination:  
        server: '{{cluster.address}}'  
        namespace: default  
      syncPolicy:  
        automated: {}
```
Make sure you update the following:

1. Update `repoURL: https://github.com/argoproj/applicationset.git` with `repoURL: https://github.com/<your account name>/applicationset.git`.
2. `spec.template.spec.project`: replace `default` with the Harness GitOps Agent Project Id.
3. `spec.template.spec.destination.server`: replace `server: https://kubernetes.default.svc` with `server: '{{cluster.address}}'`.
4. Add `syncPolicy.automated: {}` to `spec.template.spec`.
   
   ![](./static/harness-git-ops-application-set-tutorial-33.png)

5. Save your changes.

Next, we'll update the config.json files for the two target clusters.

1. Navigate to `applicationset/examples/git-generator-files-discovery/cluster-config/engineering/dev/config.json`.
2. Replace `"address": "https://1.2.3.4"` with the Endpoint IP address for the **dev** cluster. Ensure that you use the `https://` scheme.
3. Navigate to `applicationset/examples/git-generator-files-discovery/cluster-config/engineering/prod/config.json`.
4. Replace `"address": "https://1.2.3.4"` with the Endpoint IP address for the **prod** cluster. Ensure that you use the `https://` scheme.

Here's an example. Your IP addresses will be different.

![](./static/harness-git-ops-application-set-tutorial-34.png)
