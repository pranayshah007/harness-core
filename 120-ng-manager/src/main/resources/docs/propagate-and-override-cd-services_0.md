---
title: Propagate CD services
description: Use the same service across multiple stages.
sidebar_position: 2
helpdocs_topic_id: t57uzu1i41
helpdocs_category_id: ivpp0y9sdf
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to propagate CD services between stages.

You can use the same CD stage services across your pipeline stages. Once you have added a stage with a service, you can select the same service in subsequent stages by using the **Propagate from** option.

![Propagate from](static/b7df1f589cdc64982a5458c3ad1b107132e1b7e3634dfcfeb716c075437e1d6c.png)  

You can also use Harness input sets and overlays to select from different collections of settings at runtime. See [Input sets and overlays](../../../platform/8_Pipelines/input-sets.md) and [Run pipelines using input sets and overlays](../../../platform/8_Pipelines/run-pipelines-using-input-sets-and-overlays.md).
