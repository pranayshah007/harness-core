---
title: Native Helm deployment tutorial
description: This topic walks you through Native Helm deployments in Harness.
sidebar_position: 3
helpdocs_topic_id: lbhf2h71at
helpdocs_category_id: c9j6jejsws
helpdocs_is_private: false
helpdocs_is_published: true
---

This quickstart shows you how to perform Native Helm deployments using Harness.

Harness includes both Kubernetes and Native Helm deployments, and you can use Helm charts in both. Here's the difference:

* **Kubernetes with Helm:** Harness Kubernetes deployments allow you to use your own Helm values.yaml or Helm chart (remote or local), and Harness executes the Kubernetes kubectl calls to build everything without Helm and Tiller needing to be installed in the target cluster. You can perform all deployment strategies (Rolling, Canary, Blue Green).  
See [Kubernetes deployment tutorial](kubernetes-cd-quickstart), [Helm Chart deployment tutorial](helm-cd-quickstart).
* **Native Helm:**
	+ For Harness Native Helm V2 deployments, you must always have Helm and Tiller running on one pod in your target cluster and Tiller makes the API calls to Kubernetes. You can perform a Rolling deployment strategy only (no Canary or Blue Green).If you are using Helm V2, you will need to install Helm v2 and Tiller on the Delegate pod. For steps on installing software on the delegate, see [Build custom delegate images with third-party tools](/docs/platform/2_Delegates/install-delegates/build-custom-delegate-images-with-third-party-tools.md).
	+ For Harness Native Helm v3 deployments, you no longer need Tiller, but you are still limited to the Rolling deployment strategy.
		- **Versioning:** Harness Kubernetes deployments version all objects, such as ConfigMaps and Secrets. Native Helm does not.
		- **Rollback:** Harness Kubernetes deployments will roll back to the last successful version. Native Helm will not. If you did 2 bad Native Helm deployments, the 2nd one will just rollback to the 1st. Harness will roll back to the last successful version.

Harness supports Helm v2 and v3.
