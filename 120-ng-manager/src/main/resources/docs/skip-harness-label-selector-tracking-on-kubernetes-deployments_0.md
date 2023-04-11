---
title: Skip Harness label selector tracking on Kubernetes deployments
description: Prevent Harness from using its default Kubernetes label selector during canary deployments.
sidebar_position: 9
---

:::note
Currently, this feature is behind the feature flag `SKIP_ADDING_TRACK_LABEL_SELECTOR_IN_ROLLING`. Contact [Harness Support](mailto:support@harness.io) to enable the feature. 
:::

You can prevent Harness from using its default Kubernetes label selector, `harness.io/track: stable`, during canary deployments. Skipping this label can help when you have existing, non-Harness deployed services or naming conflicts. 
