## Shell Script Step

In the stage **Execution** section, add a [Shell Script](../../cd-execution/cd-general-steps/using-shell-scripts.md) step.

In **Script**, reference the artifact and any additional attributes you configured. Here's an example where the stage is named Kube:

```
echo "Version: <+pipeline.stages.Kube.spec.serviceConfig.output.artifactResults.primary.version>"  
echo "URL: <+pipeline.stages.Kube.spec.serviceConfig.output.artifactResults.primary.metadata.URL>"  
echo "Path: <+pipeline.stages.Kube.spec.serviceConfig.output.artifactResults.primary.metadata.path>"
```
