---
title: Verify Deployments with CloudWatch
description: This topic shows you how to verify deployments with CloudWatch.
sidebar_position: 4
helpdocs_topic_id: zjclrbon90
helpdocs_category_id: 9mefqceij0
helpdocs_is_private: false
helpdocs_is_published: true
---

:::note

Currently, this feature is behind the feature flag `SRM_ENABLE_HEALTHSOURCE_CLOUDWATCH_METRICS`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

Harness CV integrates with CloudWatch to:

* Verify that the deployed service is running safely and performing automatic rollbacks.
* Apply machine learning to every deployment to identify and flag anomalies in future deployments.

 This topic covers how to add and configure CloudWatch as a Health Source for the Verify step.
