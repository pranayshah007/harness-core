## Using a Delegate Inside the Cluster

Harness supports running Delegates internally in the target cluster for OpenShift 3.11 or greater.

The cluster must be configured to allow images to run as root inside the container in order to write to the filesystem. You can overcome this requirement using disk permissions.

For example, you can set the securityContext appropriately in your pod spec of the Delegate where the user and group IDs align with what is specified in the image:


```yaml
...  
  securityContext:  
    runAsUser: 1000  
    runAsGroup: 3000  
    fsGroup: 2000  
...
```
In addition, you should use the **non-root-openshift** or **non-root-ubi** Delegate image, available on [Docker Hub](https://hub.docker.com/r/harness/delegate/tags).
