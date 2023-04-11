## Infrastructure definitions

Harness has made infrastructure definitions a lighter configuration you can reuse for other ECS services.

The ECS infrastructure definition no longer has ECS service-specific properties like `Networking`, `ExecutionRoleARN`, and `AWSVPC`. These have been moved to the ECS service definition.
