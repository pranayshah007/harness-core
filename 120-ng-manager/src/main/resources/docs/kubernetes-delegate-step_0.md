---
title: Canary Delete Step
description: Clean up deployed Kubernetes workloads.
sidebar_position: 3
helpdocs_topic_id: 922mtcvank
helpdocs_category_id: 85tr1q4hin
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes the **Canary Delete** step.

The **Canary Delete** step is used to clean up the workload deployed by the [Canary Deployment](canary-deployment-step.md) step.

The Canary Delete step usually follows a Canary Deployment step.

If the **Canary Deployment** step is successful, the stage will move onto the Primary step group and the Rolling Deployment step. The workload created by the **Canary Deployment** step is no longer needed.
