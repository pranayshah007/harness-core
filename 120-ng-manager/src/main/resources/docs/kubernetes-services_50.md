# Variables

In the **Variables** section of the service you can add variables are use them in values YAML and params files, or in other settings in your stage that support expressions.

![Harness service variables section](static/bfa1c1a5cd2c491e40250728317dee1918adfd779e29ef31c8baf9e4f32ad66f.png)

You can use the variable in your values YAML files. For example, here's a service variable used in the `name` key in the values YAML:

<details>
<summary>Service variable in values YAML file</summary>

```yaml
name: <+serviceVariables.appname>  
replicas: 2  
  
image: <+artifacts.primary.image>  