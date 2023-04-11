---
title: Verify Deployments with Sumo Logic
description: This topic shows you how to verify deployments with Sumo Logic. 
sidebar_position: 12
helpdocs_is_private: false
helpdocs_is_published: true
---

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```

:::note
Currently, this feature is behind the feature flag `SRM_SUMO`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.
:::

Harness Continuous Verification (CV) integrates with Sumo Logic to:

* Verify that the deployed service is running safely and performing automatic rollbacks.
* Apply machine learning to every deployment to identify and flag anomalies in future deployments.

This topic describes how to set up a Sumo Logic health source when adding a CV step to your Continuous Deployment (CD).
