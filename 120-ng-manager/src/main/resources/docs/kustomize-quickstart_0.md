---
title: Kustomize deployment tutorial
description: This topic walks you through deploying a kustomization using Harness.
sidebar_position: 4
helpdocs_topic_id: uiqe6jz9o1
helpdocs_category_id: c9j6jejsws
helpdocs_is_private: false
helpdocs_is_published: true
---

Harness supports [Kustomize](https://kustomize.io/) kustomizations in your Kubernetes deployments. You can use overlays, multibase, plugins, sealed secrets, etc, just as you would in any native kustomization.

This Kustomize tutorial will deploy multiple variants of a simple public Hello World server using a [rolling update strategy](../../cd-execution/kubernetes-executions/create-a-kubernetes-rolling-deployment.md) in Harness.
