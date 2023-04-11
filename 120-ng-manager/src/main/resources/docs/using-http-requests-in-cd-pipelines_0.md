---
title: Using HTTP Requests in CD Pipelines
description: This topic shows you how to use the HTTP step to run HTTP methods containing URLs, methods, headers, assertions, and variables.
sidebar_position: 1
helpdocs_topic_id: 0aiyvs61o5
helpdocs_category_id: y6gyszr0kl
helpdocs_is_private: false
helpdocs_is_published: true
---

You can use the HTTP step to run HTTP methods containing URLs, methods, headers, assertions, and variables. It helps you avoid having script cURL commands for simple REST calls.

The most common use of the HTTP step is to run a health check post-deployment. For example, make sure that an HTTP or IP endpoint, such as a load balancer, is properly exposed.

Other common uses are:

* Making a call to a third-party system to gather deployment information, such as a Nexus IQ scan.
* Open Policy Agent (OPA) policy agent call.
* General HTTP testing calls to the deployed application.

This topic describes how to use the HTTP step. For comprehensive details on each setting, see [HTTP Step Reference](../../cd-technical-reference/cd-gen-ref-category/http-step.md).

Looking for the Harness REST API? See [API Quickstart](../../../platform/16_APIs/api-quickstart.md).
