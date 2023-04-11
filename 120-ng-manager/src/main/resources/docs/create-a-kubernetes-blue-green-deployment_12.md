## Prepare

Typically, in a **Prepare** section, you can see that each release of the resources is versioned. This is used in case Harness needs to rollback to a previous version.

In the case of Blue Green, the resources are not versioned because a Blue Green deployment uses **rapid rollback**: network traffic is simply routed back to the original instances.

You do not need to redeploy previous versions of the service/artifact and the instances that comprised their environment.

The **Prepare** section shows that Harness has prepared two services, identified the deployment as blue, and pointed the stage service (blue) at the blue pod set for the deployment:

This example uses one Kubernetes service, hence the use of the `-stage` suffix.

```
Manifests processed. Found following resources:   
  
Kind                Name                                    Versioned   
Service             bgdemo-svc                              false       
Deployment          bgdemo                                  false       
  
Primary Service is bgdemo-svc  
  
Created Stage service [bgdemo-svc-stage] using Spec from Primary Service [bgdemo-svc]  
  
Primary Service [bgdemo-svc] not found in cluster.  
  
Stage Service [bgdemo-svc-stage] not found in cluster.  
  
Primary Service is at color: green  
  
Stage Service is at color: blue  
  
Cleaning up non primary releases  
  
Current release number is: 1  
  
Versioning resources.  
  
Workload to deploy is: Deployment/bgdemo-blue  
  
Done.
```
