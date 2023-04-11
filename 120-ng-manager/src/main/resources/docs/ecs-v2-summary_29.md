## ECS service configuration

Although you can use the Harness [File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) for local file storage in your account, for production we recommend storing ECS manifests in remote stores like Github, Bitbucket, AWS S3, etc. Remote stores support version control on files for tracking and reverting changes.

Harness recommends using Harness service variables to template ECS service parameters. For example, you can refer to service variables in your manifests using variable expressions such as `<+serviceVariables.serviceName>`. For more information, go to [Built-in and Custom Harness Variables Reference](../../../platform/12_Variables-and-Expressions/harness-variables.md).
