## Syncing

You can sync using the Harness ApplicationSet Application or sync the individual Harness Applications independently.

![](./static/harness-git-ops-application-set-tutorial-63.png)

If you configure automatic syncing in the ApplicationSet template, then the Applications will be synced automatically. See `syncPolicy.automated` here:


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

The `syncPolicy.automated` is applied to all Applications created by the ApplicationSet because it is part of the template.

If you make a change to one of the target Applications and then perform an ApplicationSet sync, the change to target Application will be preserved.

