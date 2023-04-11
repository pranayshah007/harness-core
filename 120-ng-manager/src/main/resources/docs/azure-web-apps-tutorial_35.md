### Using Secrets and Variables Settings

You can use [Harness secrets](../../../platform/6_Security/2-add-use-text-secrets.md) and Service or Workflow variables in the **Application settings** and **Connection strings** in the Harness Service.

These settings use JSON, so ensure that you use quotes around the variable or secret reference:

```json
  {  
    "name": "PASSWORD",  
    "value": "<+secrets.getValue('doc-secret')>",  
    "slotSetting": false  
  },
```