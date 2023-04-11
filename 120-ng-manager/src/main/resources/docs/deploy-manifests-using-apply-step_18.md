# Override value

You can override some or all of the values in the values.yaml file selected in the Harness service **Service Definition** or environment overrides.

Values YAML files can be specified at several places in Harness:

* Environment service overrides (if you are using [Services and Environments v2](../../onboard-cd/cd-concepts/services-and-environments-overview.md))
* Environment configuration (if you are using [Services and Environments v2](../../onboard-cd/cd-concepts/services-and-environments-overview.md))
* Service definition manifests

You can also add a values YAML values and/or files in the Apply Step **Override Value**.

1. Click **Add Manifest**.
2. Select a values YAML type and click **Continue**.
3. Select a values YAML store. You can select remote or inline.
   - If you selected a remote store, select or add a connector to that repo, and then enter a path to the folder or file.
   - If you selected **Inline**, enter a name for you to identify values YAML and the `name:value` pairs for the override.
4. Click **Submit**. 

You can add multiple overrides to the Apply step.
