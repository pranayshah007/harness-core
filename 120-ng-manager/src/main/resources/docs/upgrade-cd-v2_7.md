## Services

The following changes apply to services:

- To use the service in a pipeline, service definitions must be configured via API/UI/YAML.
- The service definition is a configuration mapped to the service irrespective of the pipelines where it is used.
- For details on service v2, go to [Services and environments overview](../cd-concepts/services-and-environments-overview.md).
- The service entity is now moved from the pipeline to a standalone entity. The service contains the following components:
  - **Name**, **Description**, **Tag**, **Id**. These are the same as in the service v1 experience.
  - Manifests and artifacts. The service manifests and artifacts are now mapped in the service. They are moved out of the pipeline **Service** tab.
  - Service variables. Service variables are now associated with the Service and can be overridden at the environment level.

