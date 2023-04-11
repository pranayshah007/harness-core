## Supported stores for Serverless Lambda YAML files

Harness can fetch your YAML files and packaged code from the following stores:

- AWS S3 buckets.
  - You can store the serverless.yml and the artifact code in AWS S3, including in the same bucket.
  - You can use the .Zip format to grab the serverless.yaml and the packaged code that has been bundled in .zip and published in S3.
  - Harness will extrapolate the serverless.yaml file and use that for deployment.
  - For S3, you use a Harness AWS Connector. The IAM role permissions required by Harness for S3 are described in [AWS Connector Settings Reference](../../../platform/connectors/../7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md#aws-s3).
- Git providers.
