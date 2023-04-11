---
title: Create a Kubernetes Rolling Deployment
description: This topic describes how to perform a Kubernetes rolling update deployment in Harness.
sidebar_position: 1
helpdocs_topic_id: xsla71qg8t
helpdocs_category_id: uj8bqz9j0q
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to perform a Kubernetes rolling update deployment in Harness.

A [rolling update strategy](../../cd-deployments-category/deployment-concepts.md) updates Kubernetes deployments with zero downtime by incrementally updating pod instances with your new app version. New pods are scheduled on nodes using the available resources.

This method is similar to a standard Canary strategy, but different from the Harness Kubernetes Canary strategy. The Harness Kubernetes Canary strategy uses a canary phase followed by a rolling update as its final phase. See [Create a Kubernetes Canary Deployment](create-a-kubernetes-canary-deployment.md) for more information.

For a detailed explanation of Kubernetes rolling updates, see [Performing a Rolling Update](https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/) from Kubernetes.### Before you begin

* [Kubernetes CD Quickstart](../../onboard-cd/cd-quickstarts/kubernetes-cd-quickstart.md)
* [Add Kubernetes Manifests](../../cd-advanced/cd-kubernetes-category/define-kubernetes-manifests.md)
* [Define Your Kubernetes Target Infrastructure](../../cd-infrastructure/kubernetes-infra/define-your-kubernetes-target-infrastructure.md)
