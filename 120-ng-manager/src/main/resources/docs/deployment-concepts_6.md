# Blue/Green deployment

With Blue/Green Deployment, two identical environments for staging and production traffic run simultaneously with different versions of the service.

QA and UAT are typically done on the stage environment. When satisfied, traffic is flipped (via a load balancer) from the prod environment (current version) to the stage environment (new version).

You can then decommission the old environment once deployment is successful.

Some vendors call this a red/black deployment.

![](./static/deployment-concepts-01.png)
