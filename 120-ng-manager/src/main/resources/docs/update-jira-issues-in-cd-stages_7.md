# Step 2: Add Issue Fields

In Jira Fields, you can select specific fields to update within a Jira issue. For more information, see [Jira Custom Fields](../../../first-gen/continuous-delivery/model-cd-pipeline/workflows/jira-integration.md#jira-custom-fields).

![](./static/update-jira-issues-in-cd-stages-18.png)

Harness supports only Jira fields of type `Option`, `Array`, `Any`, `Number`, `Date`, and `String`. Harness does not integrate with Jira fields that manage users, issue links, or attachments. This means that Jira fields like Assignee and Sprint are not accessible in Harness' Jira integration.### Review: Jira Date Field Support

Among the custom fields Harness supports are Baseline End Date and Start Date Time. If your Jira project uses these fields, they are available in Fields:

![](./static/update-jira-issues-in-cd-stages-19.png)

Once you have selected these fields their settings appear.

![](./static/update-jira-issues-in-cd-stages-20.png)

You can also use advanced dates using stage variables and the `current()` function. For example:

* `<+currentDate().plusDays(2).plusMonths(1)>`: current date plus one month and two days.
* `<+currentTime()>`: for current date time fields.

Harness supports the following functions.

For date-only fields:

 `currentDate().plusYears(1).plusMonths(1).plusWeeks(1).plusDays(1)`

For date and time fields:

`currentTime().plusYears(1).plusMonths(1).plusWeeks(1).plusDays(1).plusHours(1).plusMinutes(1).plusSeconds(1).plusNanos(1)`

The number 1 is used as an example. You can add whatever number you need.
