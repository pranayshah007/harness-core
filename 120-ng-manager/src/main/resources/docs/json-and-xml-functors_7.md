## select()

* **Syntax:** `xml.select(string, string)`
* **Description:** Returns XML file.
* **Parameters:** String using an XPath expression and XML file, and a string for `httpResponseBody`.

**Example:**

Here is the contents of the XML file we will query:


```xml
<?xml version="1.0"?>  
<bookstore>  
  <book category="cooking">  
    <title lang="en">Everyday Italian</title>  
    <author>Giada De Laurentiis</author>  
    <year>2005</year>  
    <price>30.00</price>  
  </book>  
  <book category="children">  
    <title lang="en">Harry Potter</title>  
    <author>J K. Rowling</author>  
    <year>2005</year>  
    <price>29.99</price>  
  </book>  
  <book category="web">  
    <title lang="en">XQuery Kick Start</title>  
    <author>James McGovern</author>  
    <author>Per Bothner</author>  
    <author>Kurt Cagle</author>  
    <author>James Linn</author>  
    <author>Vaidyanathan Nagarajan</author>  
    <year>2003</year>  
    <price>49.99</price>  
  </book>  
  <book category="web" cover="paperback">  
    <title lang="en">Learning XML</title>  
    <author>Erik T. Ray</author>  
    <year>2003</year>  
    <price>39.95</price>  
  </book>  
</bookstore>
```

We are using the example at <https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/books.xml>.

Here is the query using the `xml.select()` method to select the title from the first book:


```bash
<+xml.select("/bookstore/book[1]/title", httpResponseBody)>
```
We can add the `xml.select()` method to an HTTP step and output it using **Response Mapping**:

![](./static/json-and-xml-functors-13.png)

Next, we reference the output variable **select** in a Shell Script step:


```bash
echo <+pipeline.stages.Functors.spec.execution.steps.XML_select.output.outputVariables.select>
```
When the Workflow is deployed, the result is:


```bash
Executing command ...  
  
Everyday Italian  
  
Command completed with ExitCode (0)
```

You can also see the entire XML file in the deployment **Details** section:

![](./static/json-and-xml-functors-14.png)
