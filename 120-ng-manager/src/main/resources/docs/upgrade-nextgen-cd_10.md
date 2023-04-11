# Upgrading to NextGen CD

Added an account setting, `allowCrossGenerationAccess`, that you can use to enable cross generational access between CD FirstGen and NextGen. This setting is visible to CD subscribed (free/paid account) users only. This setting can be changed by account admins only. 

If this setting is set to true, you can switch between CD FirstGen and NextGen with the click of a button. This feature is currently behind the feature flag, `PLG_ENABLE_CROSS_GENERATION_ACCESS`.

To upgrade to NextGen, select the **Launch Harness Next Generation** button in the Harness FirstGen UI. 

![](./static/launch-harness-next-gen.png)

To go back to FirstGen, select the **Launch First Generation** button in the Harness NextGen UI.

The default `allowCrossGenerationAccess` value for accounts with `defaultExperience` as NextGen is `TRUE`. 

The default `allowCrossGenerationAccess` value for accounts with `defaultExperience` as FirstGen is:

* `TRUE` if the feature flag, `PL_HIDE_LAUNCH_NEXTGEN` is disabled.  
* `FALSE` if the feature flag, `PL_HIDE_LAUNCH_NEXTGEN` is enabled.

For new FirstGen and NextGen user accounts, this value is set to `FALSE` by default.

When you change the `allowCrossGenerationAccess` setting value in FirstGen or NextGen, a FirstGen or NextGen audit is generated respectively.

| `allowCrossGenerationAccess` | FirstGen | NextGen |
| :---| :--- | :--- |
| `TRUE` | The **Launch Harness Next Generation** button will be visible in the UI | The **Launch First Generation** button will be visible in the UI |
| `FALSE` | UI will not show the **Launch Harness Next Generation** button | UI will not show the **Launch First Generation** button |

Reach out to your assigned Customer Success Manager (CSM), Account Executive, or your Customer Success Engineer (CSE) for help upgrading to Harness NextGen, or for any further queries or comments. 
