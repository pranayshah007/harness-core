## Input fields for account level migration

- `Authorization`. The auth bearer token. It can be extracted from header of network calls from the browser after logging into Harness.
- `accountIdentifier`. The user account identifier.
- `orgIdentifier`. Organization identifier of the pipeline you want to migrate.
- `projectIdentifier`. Project identifier of the pipeline you want to migrate.
- `infraIdentifierFormat`. The format for the infrastructure definition identifier. Harness will replace the expressions in this string with actual values and use it as an identifier to create an infrastructure definition.
- `templateMap`. Mapping of source template to target template.
  - `source template`. This refers to a stage template that exists in a CD stage's YAML.
  - `target template`. This refers to a stage template that replaces the existing source template in a CD stage's YAML.
  - `skipInfras`. The list of infrastructure identifiers to skip during migration. This allows you to omit infrastructures you don't want to upgrade.
  - `skipServices`. The list of service identifiers to skip during migration. This allows you to omit services you don't want to upgrade.
  - `isUpdatePipeline`. The pipeline YAML is updated with the new service and environment framework if this label is `true`. Otherwise, the pipeline YAML is not updated.
