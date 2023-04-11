### Streaming Logs Limitations for both Azure Container and Non-Containerized Deployments

You might face timeout issues as a result of limitations with streaming Web App slot deployment logs. For example, you might see `java.net.SocketTimeoutException: timeout` or some other socket errors as a result of the Azure SDK client.

Harness is working with the Azure team for a resolution (see [issue 27221](https://github.com/Azure/azure-sdk-for-java/issues/27221)). At this time, you can use a Harness [HTTP step](../../../first-gen/continuous-delivery/model-cd-pipeline/workflows/using-the-http-command.md) to verify that the slot is up and ready.
