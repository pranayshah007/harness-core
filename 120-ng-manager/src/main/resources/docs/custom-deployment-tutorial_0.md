---
title: Custom deployments using Deployment Templates tutorial
description: This topic walks you through a custom deployment in Harness using Deployment Templates to connect to target platforms, obtain target host information, and execute deployment steps.
sidebar_position: 10
helpdocs_topic_id: 6k9t49p6mn
helpdocs_category_id: c9j6jejsws
helpdocs_is_private: false
helpdocs_is_published: true
---

:::note

Currently, this feature is behind the feature flag `NG_SVC_ENV_REDESIGN`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.Harness CD supports all of the major platforms and we're adding more all the time.

:::

In some cases, you might be using a platform that does not have first class support in Harness, such as OpenStack, WebLogic, WebSphere, Google Cloud functions, etc. We call these non-native deployments.

For non-native deployments, Harness provides a custom deployment option using Deployment Templates.

Deployment Templates use shell scripts to connect to target platforms, obtain target host information, and execute deployment steps.

This tutorial will walk you through a very simple Deployment Templates using Kubernetes. Harness includes first-class Kubernetes support (see [Kubernetes deployment tutorial](kubernetes-cd-quickstart.md)), but we will use it in this tutorial as it is a very simple way to review Deployment Templates features.
