### Template parameters

ApplicationSets use generators to generate parameters that are substituted into the `template:` section of the ApplicationSet resource during template rendering.

There are many types of generators. For the list, go to [Generators](https://argocd-applicationset.readthedocs.io/en/stable/Generators/) from Argo CD docs.Generators support parameters in the format `{{parameter name}}`.

For example, here's the template section of a guestbook List generator that uses `{{cluster.name}}` and `{{cluster.address}}`:


```yaml
  template:  
    metadata:  
      name: '{{cluster.name}}-guestbook'  
    spec:  
      project: 191b68fc  
      source:  
        repoURL: https://github.com/johndoe/applicationset.git  
        targetRevision: HEAD  
        path: "examples/git-generator-files-discovery/apps/guestbook"  
      destination:  
        server: '{{cluster.address}}'  
        namespace: default  
      syncPolicy:  
        automated: {}
```

The values for these parameters will be taken from the cluster list config.json `cluster.name` and `cluster.address`:

```yaml
{  
  "releaseTag" : "k8s-v0.4",  
  "cluster" : {  
    "owner" : "cluster-admin@company.com",  
    "address" : "https://34.133.127.118",  
    "name" : "dev"  
  },  
  "asset_id" : "12345678"  
}
```

After substitution, this guestbook ApplicationSet resource is applied to the Kubernetes cluster:

```yaml
apiVersion: argoproj.io/v1alpha1  
kind: Application  
metadata:  
  name: dev-guestbook  
spec:  
  source:  
    repoURL: https://github.com/johndoe/applicationset.git  
    path: examples/git-generator-files-discovery/apps/guestbook  
    targetRevision: HEAD  
  destination:  
    server: https://34.133.127.118  
    namespace: default  
  project: 191b68fc  
  syncPolicy:  
    automated: {}
```
