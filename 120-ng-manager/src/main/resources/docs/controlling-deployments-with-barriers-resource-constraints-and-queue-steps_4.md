## Resource Constraints

Resource Constraints protect resource capacity limits by preventing simultaneous deployments to the same Service + Infrastructure combination. The Service + Infrastructure combination acts as a fixed key.

Resource Constraints are added to every Stage by default, but it can be disabled in a Stage's **Infrastructure** settings by enabling the **Allow simultaneous deployments on the same infrastructure** option.

See [Pipeline Resource Constraints](deployment-resource-constraints.md).

The automatic **Resource Constraints** setting does not apply to [Custom Stages](../../platform/8_Pipelines/add-a-custom-stage.md). **Resource Constraints** apply to a combination of Service + Infrastructure, and Custom Stages have no Services or Infrastructures. You can use Barriers and Queue steps in any stage types.
