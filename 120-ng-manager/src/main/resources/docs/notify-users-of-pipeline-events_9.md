# Option: Microsoft Teams Notifications

For Microsoft Teams notifications, you enter in the Webhook URL for your Microsoft Teams Channel in **Microsoft Teams Webhook URL**.

You create a channel connector in Microsoft Teams to generate the Webhook Harness needs for notification.

In Microsoft Teams, right-click the channel where you want to send notifications, and select **Connectors**.

![](./static/notify-users-of-pipeline-events-06.png)

In **Connectors**, locate **Incoming Webhook**, and click **Configure.**

![](./static/notify-users-of-pipeline-events-07.png)

In **Incoming Webhook**, enter a name, such as **Harness**.

Right-click and save the Harness icon from here:

![](./static/notify-users-of-pipeline-events-08.png)

Click **Upload Image** and add the Harness icon you downloaded.

Next, you'll create the Webhook URL needed by Harness.

In your Microsoft Teams Connector, click **Create**. The Webhook URL is generated.

![](./static/notify-users-of-pipeline-events-09.png)

Click the copy button to copy the Webhook URL, and then click **Done**.

The channel indicates that the Connector was set up.

![](./static/notify-users-of-pipeline-events-10.png)

In Harness, in **Notification Method** settings, enter the Webhook URL for your Microsoft Teams Channel in **Microsoft Teams Webhook URL** or add it as an [Encrypted Text](../../../platform/6_Security/2-add-use-text-secrets.md) and reference it here.

For example, if you have a text secret with the identifier `teamswebhookURL`, you can reference it like this:​


```bash
<+secrets.getValue("teamswebhookURL")>​​
```
You can reference a secret within the Org scope using an expression with `org`:


```bash
<+secrets.getvalue("org.your-secret-Id")>​
```
You can reference a secret within the Account scope using an expression with `account`:​


```bash
<+secrets.getvalue("account.your-secret-Id")>​​
```
