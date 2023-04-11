# Define Instance Attributes

Now that you have provided a path to the instances array, you can map any useful JSON keys in **Instance Attributes**.

**Important** The `instancename` value in the Field Name setting is **mandatory**.

![](./static/custom-deployment-tutorial-15.png)

You must use `instancename` to identify the target host(s) in the JSON array.

For our Kubernetes example, we will use:

* **Field Name:** `instancename`.
* **JSON Path:** `metadata.name`.

You can map any additional attributes containing information you want to reference in your Execution, most likely in a Shell Script step. See [Referencing fetched instances using expressions](#referencing_fetched_instances_using_expressions).
