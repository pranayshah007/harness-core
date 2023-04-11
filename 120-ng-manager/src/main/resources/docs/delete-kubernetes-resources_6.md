## Option: Release Name

During deployment Harness creates a ConfigMap listing the resources of the release and uses the release name for tracking them. The release name is defined in the **Infrastructure** settings, in **Cluster Details**, in **Advanced**.

If you want to delete all of the resources for a release, select **Release Name**.

If you select the **Delete namespace** option, Harness will delete the namespace(s) defined in the release.

![](./static/delete-kubernetes-resources-20.png)

Ensure that you are not deleting a namespace that is used by other deployments.### Example: Deleting a Deployment

Here is an example of the log from a Delete command:

```
Initializing..  
...  
Resources to delete are:   
- Deployment/harness-example-deployment-canary  
Done.
```
