## Using service variables in manifest YAML

Service variables are a powerful way to template your services or make them more dynamic.

In the **Variables** section of the service, you can add service variables and then reference them in any of the manifest YAML file you added to the service.

For example, you could create a variable named **entryPoint** for the manifest `entryPoint` setting and set its value as a fixed value, runtime input, or expression.

Next, in your manifest YAML file, you could reference the variable like this (see <+serviceVariables.entryPoint>):

Now, when you add that service to a pipeline, you will be prompted to enter a value for this variable in the pipeline **Services** tab. The value you provide is then used as the `entryPoint` in your manifest YAML.


