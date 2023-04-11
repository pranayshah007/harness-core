---
title: Control Resource Usage with Queue Steps
description: This topic describes how to use the Queue step to control the access order to the resources Harness requests during a deployment and prevent multiple Pipelines from requesting the same resources at the same time.
sidebar_position: 3
helpdocs_topic_id: 5n96cc7cyo
helpdocs_category_id: etz0u5kujd
helpdocs_is_private: false
helpdocs_is_published: true
---


This topic describes how to use the **Queue** step to control the access order to the resources Harness requests during a deployment and prevent multiple pipelines from requesting the same resources at the same time.

For example, two Pipelines might be deploying artifacts to a single Kubernetes namespace simultaneously. To avoid collision, and queue deployments, you add a Queue step to each Pipeline.

When the first Pipeline completes, it releases the lock and the second Pipeline can continue.

Queue steps can be used on different Pipelines or even multiple executions of the same Pipeline.

In this topic, you will learn how to use the Queue step to control the order in which Pipelines access resources.

Harness provide multiple options for controlling resource usage and protecting capacity limits. See [Controlling Resource Usage with Barriers, Resource Constraints, and Queue Steps](controlling-deployments-with-barriers-resource-constraints-and-queue-steps.md).
