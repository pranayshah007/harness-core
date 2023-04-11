## Add installation script to the Delegate YAML

1. Install the chart on the Delegate host.  
The Delegate host must have Helm installed on it. Harness installs Helm with the Delegate automatically, so you don't need to do anything unless you have removed Helm for the Delegate host.  
For information on the Helm binaries installed by default, see [Supported platforms and technologies](../../../getting-started/supported-platforms-and-technologies.md).  
You can install the chart manually on the host, but it is easier to install it using the `INIT_SCRIPT` environment variable in the Delegate YAML.
2. Add the `INIT_SCRIPT` environment variable to the StatefulSet (Legacy Delegate) or Deployment (Immutable Delegate) object in the Delegate YAML, and add your Helm chart installation script.  
For information on Kubernetes or Docker Delegate types, go to [Install a Delegate](../../../platform/2_Delegates/install-delegates/overview.md).  
For information on using `INIT_SCRIPT`, go to [Build custom delegate images with third-party tools](/docs/platform/2_Delegates/install-delegates/build-custom-delegate-images-with-third-party-tools.md).  
For information on installing Helm charts, go to Helm's documentation for [Helm Install](https://helm.sh/docs/helm/helm_install/).
