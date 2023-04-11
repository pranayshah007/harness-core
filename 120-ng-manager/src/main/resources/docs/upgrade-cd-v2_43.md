## Pipelines

The following changes apply to pipelines:

- The pipeline entity changes with the service and environment v2 update.
- The combination of service, environment, and infrastructure definitions are no longer defined in the pipeline. These entities are now managed outside of the pipeline.
- Pipelines use identifiers to reference the service, environment, and infrastructure definitions used in the pipeline.
- Each stage now has a reference to the service, environment, and infrastructure definition entities.
