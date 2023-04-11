## Swap Slot step

This step performs the Web App deployment slot swap. It's like doing a swap in the Azure portal or via the Azure CLI:

```
az webapp deployment slot swap -n "web app name" -g "resource group name" -s "source slot name" --target-slot "target slot"
```

1. Open the **Swap** **Slot** step.
  * **Target Slot:** enter the Target slot (production) for the deployment. This slot is where Harness will swap the App content and configurations elements during the **Swap Slot** step.
