---
title: Pausing pipeline execution using the Wait step
description: This topic shows you how to use the Wait step to pause a pipeline execution for any amount of time.
sidebar_position: 6
helpdocs_topic_id: ijdo3funo0
helpdocs_category_id: y6gyszr0kl
helpdocs_is_private: false
helpdocs_is_published: true
---

:::note

Currently, this feature is behind the feature flag `WAIT_STEP`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

This topic describes how to use the Wait step included in Harness pipeline stages.

Imagine you want to automatically pause and hold a pipeline execution while you check third party systems, such as checking to ensure that a Kubernetes cluster has the necessary resources, or that a database schema has been updated.

Harness pipelines include the Wait step so you can pause the pipeline execution for any amount of time. After the wait time expires, the pipeline execution proceeds.

When the Wait step is running, it provides **Mark as Success** and **Mark as Failed** options. **Mark as Success** ends the wait period and proceeds with the execution. **Mark as Failed** initiates the Failure Strategy for the step or stage, if any, or simply fails the execution.
