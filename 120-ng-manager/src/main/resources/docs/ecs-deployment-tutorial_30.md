## Supported stores for ECS manifests

Harness can fetch your task definitions, service definitions, scalable target and scaling policy configurations (in JSON or YAML) from the following stores:

- Harness File Store.
- AWS S3 buckets.
  - For S3, you use a Harness AWS Connector. The IAM role permissions required by Harness for S3 are described in [AWS Connector Settings Reference](../../../platform/connectors/../7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md#aws-s3).
- Git providers.
