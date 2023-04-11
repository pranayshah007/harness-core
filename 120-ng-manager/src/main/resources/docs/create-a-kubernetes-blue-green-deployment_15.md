# Step 4: Swap Primary with Stage Step

Click the **Swap Primary with Stage** step.

In the Prepare step you saw the primary service pointing at the green pod set and the stage service pointing at blue pod set containing the app.

In **Swap Primary with Stage**, Harness swaps the primary service to the pod set running the app (blue) and the stage service to the other color (green). Since this is the first deployment, there is no actual green pod set.

Production traffic now flows to the app.

This example uses one Kubernetes service, hence the use of the `-stage` suffix.

```
Selectors for Service One : [name:bgdemo-svc]  
  
app: bgdemo  
  
harness.io/color: green  
  
Selectors for Service Two : [name:bgdemo-svc-stage]  
  
app: bgdemo  
  
harness.io/color: blue  
  
Swapping Service Selectors..  
  
Updated Selectors for Service One : [name:bgdemo-svc]  
  
app: bgdemo  
  
harness.io/color: blue  
  
Updated Selectors for Service Two : [name:bgdemo-svc-stage]  
  
app: bgdemo  
  
harness.io/color: green  
  
Done
```

The next time you deploy, the swap will point the primary service at the green pod set and the stage service at the blue pod set:


```
...  
Swapping Service Selectors..  
  
Updated Selectors for Service One : [name:bgdemo-svc]  
  
app: bgdemo  
  
harness.io/color: green  
  
Updated Selectors for Service Two : [name:bgdemo-svc-stage]  
  
app: bgdemo  
  
harness.io/color: blue  
  
Done
```
