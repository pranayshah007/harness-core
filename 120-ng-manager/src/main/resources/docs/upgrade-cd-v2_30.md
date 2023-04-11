### Environment REST API updates

When creating a service via the Harness REST API, there is a new [enviroment endpoint](https://apidocs.harness.io/tag/Environments#operation/createEnvironmentv2).

Here are a few important details:

- The `yaml` parameter is not required for environment creation or usage in a pipeline.
- Harness has a new [environment groups API endpoint](https://apidocs.harness.io/tag/EnvironmentGroup#operation/postEnvironmentGroup).
- Harness has a new [service specific environment overrides API endpoint](https://apidocs.harness.io/tag/Environments#operation/upsertServiceOverride).
