---
title: Add a Kubernetes Sidecar Container
description: This topic describes how to deploy sidecar workloads using Harness.
sidebar_position: 7
helpdocs_topic_id: fnzak5qp3y
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to deploy sidecar workloads using Harness.

You can use Harness to deploy both primary and sidecar Kubernetes workloads. Sidecar containers are common where you have multiple colocated containers that share resources.

For details on what workloads you can deploy, see [What Can I Deploy in Kubernetes?](../../cd-technical-reference/cd-k8s-ref/what-can-i-deploy-in-kubernetes.md) Harness treats primary and sidecar workloads the same. We simply provide ways of identifying the workloads as primary and sidecar.

In the Harness Service, in addition to the manifest(s) for the primary artifact used by Harness, you simply add manifests for however many sidecar containers you need. Or you can add one manifest that includes the specs for both primary and sidecar workloads.

The containers in the manifest can be hardcoded or you can add artifact streams to Harness as Artifacts and reference them in your manifests using the `<+artifacts.sidecars.[sidecar_identifier].imagePath>` expression.

This topic provides an example of a simple sidecar deployment.
