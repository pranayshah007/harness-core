# Option: Use Plugins in Deployments

Kustomize offers a plugin framework to generate and/or transform a kubernetes resource as part of a kustomization.

You can add your plugins to the Harness Delegate(s) and then reference them in the Harness Service you are using for the kustomization.

When Harness deploys, it will apply the plugin you reference just like you would with the `--enable_alpha_plugins` parameter.
