---
title: Use multiple services and environments in a deployment
description: Deploy multiple services to multiple environments.
sidebar_position: 7
helpdocs_topic_id: tabk4u2e9n
helpdocs_category_id: etz0u5kujd
helpdocs_is_private: false
helpdocs_is_published: true
---

:::note

Currently, this feature is behind the feature flag `MULTI_SERVICE_INFRA`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

This topic describes how to use multiple Services and multiple Environments in a deployment.

Often, you will deploy one Service to one Environment in a CD stage. In some cases, you might want to use multiple Services and Environments in the same stage.

For example, let's say you host 1 infrastructure per customer and want to deploy your service to all customer infrastructures in the same pipeline. Instead of creating separate stages for each service and Infrastructure combination, you can just deploy a single service to all Infrastructures in the same stage.

Another example would be when you have multiple QA environments and what to deploy to all of them together.

With multiple Harness Services and Environments in the same CD stage, you can:

* Deploy one Service to multiple Environments.
* ​Deploy multiple Services to one Environment.
* ​Deploy multiple Services to multiple Environments.
