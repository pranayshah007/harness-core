---
title: Harness GitOps ApplicationSet and PR pipeline tutorial
description: This topic describes how to create a GitOps ApplicationSet and PR Pipeline in Harness GitOps.
sidebar_position: 7
helpdocs_topic_id: lf6a27usso
helpdocs_category_id: 013h04sxex
helpdocs_is_private: false
helpdocs_is_published: true
---


Currently, this feature is behind the feature flags `ENV_GROUP`, `NG_SVC_ENV_REDESIGN`, and `OPTIMIZED_GIT_FETCH_FILES` `MULTI_SERVICE_INFRA`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.


This tutorial shows you how to create a GitOps ApplicationSet and PR Pipeline in Harness GitOps.

In this tutorial, we'll demonstrate two major use cases:

1. We'll create an ApplicationSet defines one application and syncs it to multiple target environments.
2. We'll create a Harness PR Pipeline to change the application in just one of the target environments.

:::note

**New to ApplicationSets and PR Pipelines?** Go to [ApplicationSet and PR Pipeline Summary](#applicationset-and-pr-pipeline-summary).

:::
