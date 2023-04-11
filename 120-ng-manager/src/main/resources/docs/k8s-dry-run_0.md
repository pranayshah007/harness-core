---
title: Perform a Kubernetes dry run
description: Test your manifests before deployment.
sidebar_position: 9
---


This topic discusses how to set up the Dry Run step for a Kubernetes deployment.

The Dry Run step fetches the Kubernetes manifests or Helm charts in a stage and performs a dry run of those resources. This is the same as running a `kubectl apply --filename=manifests.yaml --dry-run`.

You can use the Dry Run step to check your manifests before deployment. You can follow the step with an [Approval](https://developer.harness.io/docs/category/approvals/) step to ensure the manifests are valid before deployment.

You can reference the resolved manifest from the Dry Run step in subsequent steps using a Harness variable expression.

```
<+pipeline.stages.[Stage_Id].spec.execution.steps.[Step_Id].k8s.ManifestDryRun>
```

:::note

When Harness performs a deployment, it compiles all values YAML files and manifests in the stage as a single manifest and applies it. The Dry Run step performs the dry run using this compiled manifest.

:::
