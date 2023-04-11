## Swap Slot step

The final step in the phase is Swap Slot. This step swaps the deployment slot you entered in the **Slot Deployment** step with the **Target Slot** mentioned in the **Swap Slots** step. It is similar to doing a swap in the Azure portal or via the Azure CLI:

```
az webapp deployment slot swap -n "web app name" -g "resource group name" -s "source slot name" --target-slot "target slot"
```
If you are new to Azure Web App deployment slot swapping, see [What happens during a swap](https://docs.microsoft.com/en-us/azure/app-service/deploy-staging-slots#what-happens-during-a-swap) from Azure.

1. Open the **Swap Slot** step.
2. In **Target Slot**, enter the name of the target (production) slot.

Once you run the Pipeline you will see the swap in the Swap Slot step logs:

```
Sending request for swapping source slot: [stage] with target slot: [production]  
Operation - [Swap Slots] was success  
Swapping request returned successfully  
Swapping slots done successfully
```
