# Harness Delegate setup

1. Install a Harness Kubernetes Delegate in a cluster. For steps on installing a Delegate, go to [Install a delegate](../../../platform/2_Delegates/install-delegates/overview.md).

  The Delegate you use for Deployment Templates should be in an environment where it can connect and query your artifact repo and target instances. Typically, you'll want a Delegate in the same subnet as the target instances.

  If your scripts will use utilities or software that does not come with the Delegate by default, you can install them on the Delegate manually or using the Delegate `INIT_SCRIPT` environment variable.

  For steps on using `INIT_SCRIPT`, see [Build custom delegate images with third-party tools](/docs/platform/2_Delegates/install-delegates/build-custom-delegate-images-with-third-party-tools.md).

  Harness Delegate installation packages include `TAR` and `cURL`. You can use `cURL` and `TAR` in your Delegate scripts and Pipeline steps without installing these tools.

  Harness Delegate installation packages also include standard tools and SDKs for the main platform integrations Harness uses, such as Kubernetes, Helm, AWS, Azure, GCP. You do not need to install their CLI tools on the Delegate.

  Once the Delegate is installed and registers with your Harness account, you will see it listed on the Delegates page.

1. Note the default tag added to the Delegate. You will use this to select this Delegate in future steps.

![](./static/custom-deployment-tutorial-13.png)
