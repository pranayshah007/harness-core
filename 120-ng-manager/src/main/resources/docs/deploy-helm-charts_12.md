# Option: Override chart values YAML in environment

You can override the values YAML file for a stage's environment by mapping the environment name to the values file or folder. Next, you use the `<+env.name>` Harness expression in the values YAML path.

Let's look at an example.

Let's say there is a repo with three values YAML files, dev.yaml, qa.yaml. prod.yaml. In the **Values YAML** setting for the values file, you use the `<+env.name>` expression.

![](./static/deploy-helm-charts-06.png)

Next, in the environment setting, you add three environments, one for each YAML file name.

When you select an environment, such as **qa**, the name of the environment is used in **File Path** and resolves to **qa.yaml**. At runtime, the **qa.yaml** values file is used, and it overrides the values.yaml file in the chart.

Instead of selecting the environment in the **Infrastructure** each time, you can set the environment as a **Runtime Input** and then enter **dev**, **qa**, or **prod** at runtime.
