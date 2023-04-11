### Terraform provider

Please review the changes to the Harness Terraform provider service resource.

- The Harness Terraform Provider [service resource endpoint](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_service) has not changed.
- The service resource payload has a new field added for service creation: `yaml`.
- `yaml` is not mandatory for service object creation.
- When creating a service without `yaml` defined, the Terraform provider will create a skeleton service that cannot be used for immediate deployment.
- The `yaml` field defines the actual definition of the service so it can be used in a pipeline for deployment.

```yaml
resource "harness_platform_service" "example" {
  identifier  = "identifier"
  name        = "name"
  description = "test"
  org_id      = "org_id"
  project_id  = "project_id"
  