# Create custom Deployment Template

You can create a Deployment Template at the Account, Org, or Project level. For this tutorial, we'll use the Project level.

1. In your Harness Project, click **Project Setup**.
2. Click **Templates**.
3. Click **New Template**.
4. Select **Deployment**.
1. In **Create New Deployment Template**, in **Name**, enter **DT**.
2. In **Version Label**, enter **v1**.In **Logo**, you can upload an image to use as the icon for this template.
  ![](./static/custom-deployment-tutorial-14.png)
3. Click **Start**.

The Deployment Template is composed of two major sections:

- **Infrastructure:**

  ![](static/dt-Infrastructure.png)

  - **Variables:** variables that can be used when the script is run.
  - **Fetch Instances Script:** script needed to fetch a JSON array of the target instances. The script can be entered here or you can use the [Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) to share scripts with others.
  - **Instance Object Array Path:** the JSON path to the label that lists the array of instances, such as `items`.
  - **Instance Attributes:** the JSON path to the instance name label for the target instances.Mapping the fieldname `instancename` to the JSON Path is mandatory.You can add more instance attributes for additional target instance values you want to use.
- **Execution:**
  
  ![](static/dt-execution.png)

  - Any steps that you want to use in the stage and associate with this template. If you create these in the Deployment Template, they are automatically created as separate Step Templates.
  - Your stage is not limited to using these steps. You can add any steps you want to your stage.

For this tutorial, we'll only use some of these settings, but the rest of the settings are described in [Notes](#notes).
