# Step 2: Add Issue Fields

In Jira Fields, you can select specific fields within a Jira issue. For more information, see [Jira Custom Fields](../../../first-gen/continuous-delivery/model-cd-pipeline/workflows/jira-integration.md#jira-custom-fields).

Harness supports only Jira fields of type `Option`, `Array`, `Any`, `Number`, `Date`, and `String`. Harness does not integrate with Jira fields that manage users, issue links, or attachments. This means that Jira fields like Assignee and Sprint are not accessible in Harness' Jira integration.
