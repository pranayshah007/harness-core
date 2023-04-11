# Using the Dry Run step output in other steps

You can reference the resolved dry run manifest from the Dry Run step using this Harness expression:

```
<+pipeline.stages.[Stage_Id].spec.execution.steps.[Step_Id].k8s.ManifestDryRun>
```

For example, if the stage Id is `Deploy` and the Dry Run step Id is `Dry_Run` the expression would be:

```
<+pipeline.stages.Deploy.spec.execution.steps.Dry_Run.k8s.ManifestDryRun>
```

You can enter the expression in subsequent steps such as the [Shell Script step](../../cd-execution/cd-general-steps/using-shell-scripts.md) or [Approval](https://developer.harness.io/docs/category/approvals/) steps.

