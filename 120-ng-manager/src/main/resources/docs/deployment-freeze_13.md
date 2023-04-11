# Notify users of freeze window events

You can notify Harness users and people outside of your Harness account using freeze window notifications.

You can notify users of the following freeze window events:

- Freeze window is enabled.
- Deployments are rejected due to freeze window. This includes any trigger invocations that are rejected due to a freeze window.

You can use the following notification methods:

- Slack
- Email
- Harness User Groups
- PagerDuty
- Microsoft Teams

To enable notifications, do the following:

```mdx-code-block
<Tabs>
  <TabItem value="Visual" label="Visual" default>
```

1. In a freeze window, click **Notify**.
2. Click **Notifications**.
3. Enter a name for the notification and click **Continue**.
4. In **Configure the conditions for which you want to be notified**, select the freeze window events that send notifications.
5. Click **Continue**.
6. In **Notification Method**, configure one of the methods described in [Add a Pipeline Notification Strategy](../cd-advanced/cd-notifications/notify-users-of-pipeline-events.md).
7. Click **Finish**.
8. Click **Apply Changes**.

```mdx-code-block
  </TabItem>
  <TabItem value="YAML" label="YAML">
```

1. In the freeze window, click **YAML**.
2. Enter the freeze window YAML notification events and method. For example, this YAML uses all events and the Email and User Group methods:
```
...
  notificationRules:
    - name: example
      identifier: example
      events:
        - type: FreezeWindowEnabled
        - type: DeploymentRejectedDueToFreeze
        - type: TriggerInvocationRejectedDueToFreeze
      notificationMethod:
        type: Email
        spec:
          userGroups:
            - account._account_all_users
          recipients:
            - john.doe@harness.io
      enabled: true
```
For examples of all methods, see [Add a Pipeline Notification Strategy](../cd-advanced/cd-notifications/notify-users-of-pipeline-events.md).

```mdx-code-block
  </TabItem>
</Tabs>
```

