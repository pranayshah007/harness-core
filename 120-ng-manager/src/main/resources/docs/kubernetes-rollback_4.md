# Blue Green Rollbacks

In the case of Blue Green, the resources are not versioned because a Blue Green deployment uses **rapid rollback**. Network traffic is simply routed back to the original instances.

You do not need to redeploy previous versions of the service/artifact and the instances that comprised their environment.
