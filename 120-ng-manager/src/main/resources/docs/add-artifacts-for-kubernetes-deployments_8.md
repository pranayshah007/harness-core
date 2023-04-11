## Sidecar artifacts

Sidecar artifacts follow the same rules as primary artifacts.  

If you do not hardcode the sidecar image location in the manifest, you will use the **Artifacts** settings in the Harness Service Definition to identify the sidecar artifact to use and select the image and tag in Harness at runtime.  

As explained above, in your Values file, you refer to the sidecar artifact you set up in Harness using the expression `<+serviceConfig.serviceDefinition.spec.artifacts.sidecars.[sidecar_identifier].spec.imagePath>:<+serviceConfig.serviceDefinition.spec.artifacts.sidecars.[sidecar_identifier].spec.tag>`.  

In this example, `[sidecar_identifier]` is the sidecar identifier you specified when you added the sidecar artifact.
