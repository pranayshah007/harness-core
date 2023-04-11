---
title: Add Kubernetes Manifests
description: This topic describes how to add Kubernetes manifests in Harness.
sidebar_position: 2
helpdocs_topic_id: ssbq0xh0hx
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to add Kubernetes manifests in Harness.

Harness provides a simple and flexible way to use Kubernetes manifests in your Harness Pipelines.

You can simply link Harness to your remote files in a Git repo. At deployment runtime, Harness fetches and applies your files.

You can also use values YAML files with your manifests to provide different sets of values for the same manifests. The manifests can use Go templating to reference values in your values YAML files.

For example, multiple values files can contain specific deployment settings, such as separate values YAML files for QA and Production values. You simply select the values file you want to use during setup. You can the same manifests in all your Pipeline stages but use one values file in the first stage of your Pipeline (for example, a DEV stage) and a different values file in the next stage (for example, a QA stage).

This topics provides a quick overview or some options and steps when using Kubernetes manifests, with links to more details.
