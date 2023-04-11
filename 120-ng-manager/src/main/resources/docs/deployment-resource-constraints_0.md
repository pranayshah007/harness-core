---
title: Pipeline Resource Constraints
description: This topic describes how to prevent multiple Pipelines or Stages from requesting the same deployment environment resources at the same time.
sidebar_position: 5
helpdocs_topic_id: jrzwrdpvm2
helpdocs_category_id: etz0u5kujd
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how pipeline resource constraints prevent multiple pipelines or stages from requesting the same deployment environment resources at the same time.

Harness automatically adds Resource Constraints to every stage, but it's important to understand how it works and how it can be disabled.
​
![](./static/deployment-resource-constraints-08.png)

Harness provide multiple options for controlling resource usage and protecting capacity limits. 

See [Controlling Resource Usage with Barriers, Resource Constraints, and Queue Steps](controlling-deployments-with-barriers-resource-constraints-and-queue-steps.md).
