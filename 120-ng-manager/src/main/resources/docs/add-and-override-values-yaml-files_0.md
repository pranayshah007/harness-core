---
title: Add and override values YAML files
description: This topic describes how to use values files for Kubernetes and Helm deployments in Harness.
sidebar_position: 4
helpdocs_topic_id: q002wjjl4d
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

You can use values YAML files for Kubernetes and Helm deployments in Harness.

For Kubernetes manifests, the values file uses Go templating to template manifest files. See [Example Kubernetes Manifests using Go Templating](../../cd-technical-reference/cd-k8s-ref/example-kubernetes-manifests-using-go-templating.md).

For Helm charts, the values file defines the default values for parsing the Kubernetes templates (manifests) when deploying the Helm chart. See [Deploy Helm Charts](../cd-helm-category/deploy-helm-charts.md).

Harness supports Kubernetes and Helm charts without requiring Helm or Tiller and Kubernetes and Helm have equal support for all Harness deployment strategies.

You can overlay and override multiple values files in a stage's Service in a few ways. For example, by overlaying multiple files and by replacing file paths dynamically at runtime.

This topic describes how to add values files, how to override them at the Service and Environment, and how to override them at Pipeline runtime.
