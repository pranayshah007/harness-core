### Infrastructure definition YAML updates

V2 has the following infrastructure definition changes:

- The infrastructure definition is now a standalone object not defined in a pipeline.
- When configuring the infrastructure definition you will associate the infrastructure definition with the environment where you want to use it. For example, the actual cluster in the environment where you want to deploy.

```yaml
infrastructureDefinition:
  name: product-staging
  identifier: productstaging
  description: ""
  tags: {}
  orgIdentifier: default
  projectIdentifier: Rohan
  