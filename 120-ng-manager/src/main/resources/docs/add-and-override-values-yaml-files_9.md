# Option: Override Values in an Environment

You can override the values YAML file for a stage's Environment by mapping the Environment name to the values file or folder. Next, you use the `<+env.name>` Harness expression in the values YAML path.

Let's look at an example.

Here is a repo with three values files, dev.yaml, qa.yaml. prod.yaml. In the **File Path** for the values file, you use the `<+env.name>` expression. 

Next, in the **Environment** setting, you add three Environments, one for each YAML file name.

![](./static/add-and-override-values-yaml-files-36.png)

When you select an Environment, such as **qa**, the name of the Environment is used in **File Path** and resolves to **qa.yaml**. At runtime, the **qa.yaml** values file is used.

Instead of selecting the Environment in the **Infrastructure** each time, you can set the Environment as a **Runtime Input** and then enter **dev**, **qa**, or **prod** at runtime. See [Runtime Inputs](../../../platform/20_References/runtime-inputs.md).
