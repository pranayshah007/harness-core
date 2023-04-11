## Using service variables in config file

Service variables are a powerful way to template your services or make them more dynamic.

In the **Variables** section of the service, you can add service variables and then reference them in any of the ASG configuration files you added to the service.

For example, you could create a variable named **desiredCapacity** and set its value as a [fixed value, runtime input, or expression](https://developer.harness.io/docs/platform/references/runtime-inputs/).

![service variable](static/c590ccda5addd62225b690d85c60237b2f6e9378e8ed4b02ba3e82ba9bda29e9.png)  

Next, in your ASG configuration file, you could reference the variable like this (see `<+serviceVariables.desiredCapacity>`):

```json
{
  "autoScalingGroupName": "lovish2-asg",
  "minSize": 1,
  "maxSize": 3,
  "desiredCapacity": <+serviceVariables.desiredCapacity>,
  "tags": [{"key": "key7", "value":  "value7"}, {"key": "key2", "value":  "value2"}],
  "availabilityZones": ["us-east-1a", "us-east-1b"]
}
```

Now, when you add that service to a pipeline, you will be prompted to enter a value for this variable in the pipeline **Services** tab. The value you provide is then used as the `desiredCapacity` in your ASG configuration.
