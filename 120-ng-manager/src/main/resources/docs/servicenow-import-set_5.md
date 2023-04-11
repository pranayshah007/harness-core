# Enter the JSON body

The **JSON Body** setting contains the JSON that this step will pass when it makes a call to ServiceNow's Import Set API. 

For example, to initiate a transformation using the `sys_import_state_comment` field of the selected staging table based on its transformation map, you would use something like this:

```
{"sys_import_state_comment":"my comment"}
```

You can also use Harness runtime inputs, variable expressions, and Harness secrets in the JSON of **JSON Body**. 

For example, you could create a stage variable named `importset` and then reference it in **JSON Body** as {"u_example":"<+stage.variables.importset>"}.

For details on the table requirements and naming, go to [Import sets](https://docs.servicenow.com/en-US/bundle/tokyo-platform-administration/page/administer/import-sets/reference/import-sets-landing-page.html) from ServiceNow.

For details on creating a transform map, go to [Create a transform map](https://docs.servicenow.com/bundle/tokyo-platform-administration/page/script/server-scripting/task/t_CreateATransformMap.html). 
