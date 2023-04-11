## Define freeze window coverage and schedule

Let's look at an account-level example that applies a freeze to all orgs and projects from July 3rd to 5th and notifies users by email (`john.doe@harness.io`) and Harness user group (All Account Users).

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
  <TabItem value="Visual" label="Visual" default>
```
1. In **Overview**, click **Continue**.
3. In **Coverage**, click **Add rule**.
4. In **Name**, enter a name for the rule. 
   
   Rules are combined. You can add multiple rules and the freeze window is applied according to the sum of all rules.

   The remaining settings will depend on whether this freeze window is being created at the account, org, or project level. In this example, we're using the account-level.
1. Click in **Organization** and select the org you want to freeze.
   
      You can also click **Exclude specific Organizations** and select the orgs you want to exclude. This can be helpful if you selected **All Organizations** in **Organization**.

2. In **Projects**, select the projects to freeze in the orgs you selected.

      You can also click **Exclude specific Projects** and select the projects you want to exclude. This can be helpful if you selected **All Projects** in **Projects**.

3. In **Environment Type**, select **All Environments**, **Production**, or **Pre-Production**. For example, this setting allows you to keep deploying pre-production app versions without worrying that production versions will be impacted.
4. Click the checkmark to add the rule.

   The coverage will look something like this:

   ![coverage](static/deployment-freeze-coverage.png)

5. Click **Continue**.

   In **Schedule**, you define when the freeze windows starts and stops.

6. In **Timezone**, select a timezone.
7. In **Start Time**, select a calendar date and time for the freeze window to start.
8. In **End Time**, select a duration (for example `1d`) or an end date and time. A minimum of `30m` is required.
   
   For a duration, you can use:
   - `w` for weeks
   - `d` for days
   - `h` for hours
   - `m` for minutes

9.  In **Recurrence**, select how often to repeat the freeze window and a recurrence end date.

   The schedule will look something like this:

   ![schedule](static/deployment-freeze-schedule.png)

10. Click **Save**.

```mdx-code-block
  </TabItem>
  <TabItem value="YAML" label="YAML">
```

1. Click **YAML**.
2. Paste the following YAML example:
```yaml
freeze:
  name: example
  identifier: example
  entityConfigs:
  # enter the rule name
    - name: myapp freeze
    # select the entities to freeze
      entities:
        - type: Org
          filterType: All
        - type: Project
          filterType: All
        - type: Service
          filterType: All
        - type: EnvType
          filterType: All
  # enable or disable the freeze window with Enabled/Disabled
  status: Disabled
  # define when the freeze windows starts and stops.
  windows:
    - timeZone: America/Los_Angeles
      startTime: 2023-07-03 10:08 AM
      endTime: 2023-07-05 10:38 AM
  description: ""
  # set the notification events and method
  notificationRules:
    - name: my team
      identifier: my_team
      events:
        - type: FreezeWindowEnabled
        - type: DeploymentRejectedDueToFreeze
      notificationMethod:
        type: Email
        spec:
          userGroups:
            - account._account_all_users
          recipients:
            - john.doe@harness.io
      enabled: true
```
```mdx-code-block
  </TabItem>
</Tabs>
```
