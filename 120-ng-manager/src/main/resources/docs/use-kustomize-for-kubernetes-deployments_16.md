# Option: Overlays and Multibases

An overlay is a kustomization that depends on another kustomization, creating variants of the common base. In simple terms, overlays change pieces of the base kustomization.yaml. These are commonly used in patches.

A multibase is a type of overlay where copies of the base use the base but make additions, like adding a namespace.yaml. Basically, you are declaring that the overlays aren't just changing pieces of the base, but are new bases.

In both overlays and multibases, the most common example is staging and production variants that use a common base but make changes/additions for their environments. A staging overlay could add a configMap and a production overlay could have a higher replica count and persistent disk.

You can add overlay YAML files to the Service Manifests section just as you would add the standard kustomization.yaml.

Harness will look for the `resources` section of the overlay file to find the kustomization.yaml for the overlay and apply them both.


```yaml
resources:  
  - ../../application
```
In some cases you might want to deploy the standard kustomization.yaml in one stage and then the overlay in another. In this case, when you create the new stage, select **Propagate from**, select the standard kustomization.yaml stage, and then select **Stage Overrides**.

In **Manifests**, add the overlay kustomization.yaml and any patch files.

See [Propagate and Override CD Services](../../cd-services/cd-services-general/propagate-and-override-cd-services.md).
