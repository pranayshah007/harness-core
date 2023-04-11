# Step 2: Select Events

In **Pipeline Events**, select the events that will trigger the notification.

You can select events for the Pipeline or stages.

If you select the stage events, you can select which stages to use.

![](./static/notify-users-of-pipeline-events-01.png)

Click **Continue**.

There are different communication and incident management platforms to use for the notification rule. Their settings are described below.

The events are self-explanatory, but there are a few considerations:

* If you select both Pipeline End and Pipeline Success, you'll get two notifications.
* Pipeline Pause only applies to a manual pause of the Pipeline execution. Pending Approval steps don't trigger the Pipeline Pause event.
