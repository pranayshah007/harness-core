---
title: Prune Kubernetes resources
description: Perform Kubernetes pruning.
sidebar_position: 8
helpdocs_topic_id: t7phv4eowh
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to perform Kubernetes pruning using Harness.

Changes to the manifests used in Harness Kubernetes deployments can result in orphaned resources you are unaware of.

For example, one deployment might deploy resources A and B but the next deployment deploys A and C. C is the new resource and B was removed from the manifest. Without pruning, resource B will remain in the cluster.

You can manually delete Kubernetes resources using the [Delete](../../cd-execution/kubernetes-executions/delete-kubernetes-resources.md) step, but you can also set Harness to perform resource pruning during deployment using the **Enable Kubernetes Pruning** setting in the **Rolling Deployment** and **Stage Deployment** (used in Blue Green deployments) steps.

![](./static/prune-kubernetes-resources-00.png)

Harness will use pruning to remove any resources that were present in an old manifest, but no longer present in the manifest used for the current deployment.

Harness also allows you to identify resources you do not want pruned using the annotation `harness.io/skipPruning`.
