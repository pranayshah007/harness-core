---
title: Add Harness GitOps Repository Credentials Template
description: This topic describes how to create a single GitOps Repository Credentials Template and apply it to all GitOps Repositories.
sidebar_position: 5
helpdocs_topic_id: tg4og0bboo
helpdocs_category_id: 013h04sxex
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to set up a GitOps Repository Credentials Template.

Harness GitOps Repositories are connections to repos containing the declarative description of a desired state. The declarative description can be in Kubernetes manifests, Helm Chart, Kustomize manifests, etc.

If you are using multiple Harness GitOps Repositories for the subfolders in the same Git or Helm repo, you don't want to add the same credentials multiple times. Instead, you can create a single GitOps Repository Credentials Template and select it when creating GitOps Repositories.

A Harness GitOps Repository is used for Harness GitOps only. For other Harness features like CI, CD Pipelines, etc, use a standard [Git Connector](../../platform/7_Connectors/add-a-git-hub-connector.md).
