---
title: Using Shell Scripts in CD Stages
description: This topic shows you how to run shell scripts in a CD stage using the Shell Script step.
sidebar_position: 2
helpdocs_topic_id: k5lu0u6i1i
helpdocs_category_id: y6gyszr0kl
helpdocs_is_private: false
helpdocs_is_published: true
---

You can run shell scripts in a CD stage using the **Shell Script** step.

With the Shell Script step, you can execute scripts in the shell session of the stage in the following ways:

* Execute scripts on the host running a Harness delegate. You can use delegate selectors to identify which Harness delegate to use.
* Execute scripts on a remote target host in the deployment infrastructure definition.

This topic provides a simple demonstration of how to create a script in a Shell Script step, publish its output in a variable, and use the published variable in a subsequent step.
