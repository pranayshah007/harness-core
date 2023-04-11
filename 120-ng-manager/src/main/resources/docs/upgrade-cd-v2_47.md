# Migrating from v1 services and environments to v2

:::note

The user(s) running the APIs for migration must have standard Harness RBAC permissions to update pipelines, services,  environments, and templates (if used).

:::

To support automated migration of services and environments, we have created two APIs.

The APIs copy over the v1 `serviceDefinition` from a pipeline stage and update the existing service with this `serviceDefinition`. 

The APIs also create an infrastructure definition by using the details from the `infrastructure.infrastructureDefinition` in the YAML of the pipeline stage.

Regardless of whether the pipeline uses templates, the API updates the pipeline YAML also. 
