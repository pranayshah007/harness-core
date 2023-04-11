# Rollback with multiple Services and Environments

With a multi Service to multi Infrastructure stage, every combination of Service and Infrastructure is treated as a separate deployment.

Consequently, if you are deploying Services A and B to Infrastructure 1 and the deployment of Service A to Infrastructure 1 fails, it will only impact the deployment of Service B to Infrastructure 1 if the Services are deployed serially (and Service A is first).

If the Services are deployed in parallel, the failure of of Service A to Infrastructure 1 will not impact the deployment of of Service B to Infrastructure 1. The failed deployment of Service A to Infrastructure 1 will roll back, but the deployment of of Service B to Infrastructure 1 will not roll back.
