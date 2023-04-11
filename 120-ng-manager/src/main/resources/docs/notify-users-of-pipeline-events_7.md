# Option: Slack Notifications

For Slack notifications, you simply create a webhook in Slack and paste it into the **Slack Webhook URL** setting in the Notification Rule.

Follow the steps in Slack documentation for creating a Slack app, selecting your channel, and creating a webhook: [Sending messages using Incoming Webhooks](https://api.slack.com/messaging/webhooks).

When you are done, you'll have a webhook that looks something like this:

![](./static/notify-users-of-pipeline-events-03.png)

Copy the webhook.

You either paste the Webhook into **Slack Webhook URL** or add it as an [Encrypted Text](../../../platform/6_Security/2-add-use-text-secrets.md) in Harness and reference it here.

For example, if you have a text secret with the identifier `slackwebhookURL`, you can reference it like this:​


```bash
<+secrets.getValue("slackwebhookURL")>​
```

You can reference a secret within the Org scope using an expression with `org`:​


```bash
<+secrets.getValue("org.your-secret-Id")>​​
```

You can reference a secret within the Account scope using an expression with `account`:​


```bash
<+secrets.getValue(“account.your-secret-Id”)>​​​
```
![](./static/notify-users-of-pipeline-events-04.png)
