## object()

* **Syntax:** `object(string)`
* **Description:** Selects objects from a JSON collection.
* **Parameters:** string. This is a JSON key used to identify the desired JSON attribute value from a JSON response. Typically, `httpResponseBody`.

**Example:**

Here is the JSON we will query:


```json
{"item":"value1","level1":{"level2":"value2"}}
```
You can find this example at <https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/object.json>.

Here is the query using the `object()` method to select `value1`:


```bash
<+json.object(httpResponseBody).item>
```
We can add the `object()` method to an HTTP step and output it:

![](./static/json-and-xml-functors-09.png)

When this HTTP step is executed, in its **Output** tab, you can see the HTTP response in **HTTP Response Body** and the object in the **Output Variables**:

![](./static/json-and-xml-functors-10.png)
