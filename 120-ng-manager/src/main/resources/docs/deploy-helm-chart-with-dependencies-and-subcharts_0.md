---
title: Deploying Helm charts with subcharts
description: This topic describes how to define Helm subcharts and dependencies in the service YAML.
sidebar_position: 2
---

Helm charts can have dependencies called subcharts. You can define subcharts in your service YAML. Helm downloads these dependencies from exisiting or seperate repositories. Harness fetches the defined subcharts during pipeline execution.

:::note
This feature is currently behind the feature flag, `NG_CDS_HELM_SUB_CHARTS`. Contact [Harness Support](mailto:support@harness.io) to enable this feature. 
:::
