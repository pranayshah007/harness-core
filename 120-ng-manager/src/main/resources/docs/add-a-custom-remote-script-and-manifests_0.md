---
title: Add a custom remote script and manifests
description: Run a script at deployment runtime and pull in your manifests.
sidebar_position: 9
helpdocs_topic_id: fjee1zl72m
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to use Custom Remote Manifests to run a script at deployment runtime and pull in your manifests.

Harness provides first-class support for all the major devops platforms manifests and specifications, but there are situations when you want to use a custom script to pull your manifests.

Harness provides Custom Remote Manifests to let you run your script at deployment runtime and pull in your manifests.

In some cases, your manifests are in a packaged archive and you simply wish to extract and use them at runtime. In these cases, you can use a packaged archive with Custom Remote Manifests.

You simply use Custom Remote Manifests to add a script that pulls the package and extracts its contents. Next, you supply the path to the manifest or template for Harness to use.

Custom Remote Manifests are supported for:

* Kubernetes
* Helm Chart
* OpenShift

Looking for other methods? See [Add Kubernetes Manifests](define-kubernetes-manifests.md).
