# Override service settings

 You can override a service's setting by using **Environment Overrides** and by overlaying values YAML files.
 
For details on **Environment Overrides**, see [Services and environments overview](../../onboard-cd/cd-concepts/services-and-environments-overview.md).The following information covers overriding Services in Services and Environments v1 only.

A common method for overriding values YAML files is to use the `<+env.name>` Harness expression in the **File Path** of the values YAML file and then name your Harness environments with the same names as your values YAML files.

<!-- ![](./static/6cffc4e7fc1159c37eece2bb6cc2a7e76a115652155fe93c91a3f80a68328112.png) -->

<docimage path={require('./static/6cffc4e7fc1159c37eece2bb6cc2a7e76a115652155fe93c91a3f80a68328112.png')} />

There are other ways to override the values.yaml files without using environments.

You can overlay values files in Harness by adding multiple files or you can replace file paths dynamically at runtime.

![overlay](static/0bbc97758875d869b84bcf9ee6648103f217ecd0923076a0f2d86f3c821e0df7.png)

See [Add and override values YAML files](../../cd-advanced/cd-kubernetes-category/add-and-override-values-yaml-files.md).

