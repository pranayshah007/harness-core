# Option: PagerDuty Notifications

For PagerDuty notifications, enter the key for a PagerDuty Account/Service or add it as an [Encrypted Text](../../../platform/6_Security/2-add-use-text-secrets.md) in Harness and reference it in **PagerDuty Key**. Harness will send notifications using this key.

For example, if you have a text secret with the identifier `pagerdutykey`, you can reference it like this:​


```bash
<+secrets.getValue("pagerdutykey")>​
```
You can reference a secret within the Org scope using an expression with `org`:


```bash
<+secrets.getvalue("org.your-secret-Id")>
```

You can reference a secret within the Account scope using an expression with `account`:


```bash
<+secrets.getvalue("account.your-secret-Id")>
```

You can copy/paste this key from PagerDuty's **Configuration** > **Services** > **Service Details** dialog > **Integrations** tab, as shown below.

![](./static/notify-users-of-pipeline-events-05.png)

For details, see PagerDuty's documentation on [Creating Integrations](https://support.pagerduty.com/docs/services-and-integrations).
