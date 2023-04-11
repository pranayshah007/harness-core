## Blue Green deployments

Harness recommends using an [Approval](/docs/category/approvals) step between the ECS Blue Green Create Service and ECS Blue Green Swap Target Groups steps. Approval steps can verify new service deployment health before shifting traffic from the old service to the new service.

For critical services with high availability requirements, Harness recommends enabling the **Do not does not downsize the old service** option. This method can help in faster rollbacks as the rollback process only switches traffic at the load balancer.
