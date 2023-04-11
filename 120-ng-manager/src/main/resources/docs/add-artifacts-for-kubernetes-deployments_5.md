## Public artifact is not hardcoded in the manifest

In this scenario, you'll use the **Artifacts** settings in the Harness Service Definition to identify the artifact to use and select the image and tag in Harness at runtime.

+ **Primary artifact:** In your manifest, you refer to the primary artifact you set up in Harness using the expression `<+artifact.image>`: `image: <+artifact.image>`.
+ **Sidecar artifacts:** In your manifest, you refer to the sidecar artifact you set up in Harness using the expression `<+serviceConfig.serviceDefinition.spec.artifacts.sidecars.[sidecar_identifier].spec.imagePath>:<+serviceConfig.serviceDefinition.spec.artifacts.sidecars.[sidecar_identifier].spec.tag>`.  
In this example, `[sidecar_identifier]` is the sidecar identifier you specified when you added the sidecar artifact to Harness.
