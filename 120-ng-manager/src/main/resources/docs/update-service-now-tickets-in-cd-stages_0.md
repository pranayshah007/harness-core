---
title: Update ServiceNow Tickets in CD Stages
description: Update Service Now tickets in CD Stages.
sidebar_position: 4
helpdocs_topic_id: ljoofj2839
helpdocs_category_id: qd6woyznd7
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to update Service Now tickets in CD Stages.

Harness provides the ability to update [ServiceNow change requests](https://docs.servicenow.com/bundle/rome-it-service-management/page/product/change-management/concept/c_ITILChangeManagement.html) and [incident tickets](https://docs.servicenow.com/bundle/rome-it-service-management/page/product/incident-management/concept/c_IncidentManagement.html) from your Pipeline step using the **ServiceNow Update** step.

You can add the Create ServiceNow step to a Harness CD stage or an Approval stage and do the following:

* Automatically update change requests in ServiceNow to track updates to your build, test, and production environments by adding a **ServiceNow Create** step in your Pipeline.
* Automatically assign tickets in ServiceNow to triage and resolve incidents quickly in case of Pipeline failures.
