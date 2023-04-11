# Resource Constraints Summary

Harness will only allow one Stage to deploy to the same Service and Infrastructure Definition combination at the same time. The Stages are typically in different Pipelines, but Resource Constrain also apply to Stages in the same Pipeline run in parallel.

Harness queues deployments to ensure that two Service and Infrastructure Definition combinations are not deploying at the same time.

Once the first Stage in the queue is done deploying, the next Stage in the queue can deploy to that Service and Infrastructure Definition combination.
