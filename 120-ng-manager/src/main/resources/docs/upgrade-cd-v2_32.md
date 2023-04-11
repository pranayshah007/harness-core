### Terraform provider

Please review the changes to the Harness Terraform Provider environment resource.

- The Harness Terraform provider [environment resource endpoint](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment) has not changed.
- The environment resource payload has a new field added for environment creation: `yaml`.
- `yaml` is not mandatory for environment object creation.
- Harness has a new [resource endpoint for environment groups](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment_group).
- Harness has a new [resource endpoint for environment service configuration overrides](https://registry.terraform.io/providers/harness/harness/latest/docs/resources/platform_environment_service_overrides).
