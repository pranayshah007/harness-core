 Include all settings from the root terragrunt.hcl file  
include {  
  path = find_in_parent_folders()  
}  
  
inputs = {  
  tfmodule3 = "tfmodule4"  
  slmodule3 = "sleepmodule4"  
  tfv = "tfversion1"  
  sl = "sl1"  
}
```

You have two options:

- **All Modules**. Harness will use all of the terragrunt.hcl files starting from the *folder* you specify in **Path**.
  
  If you select **All Modules**, you might want to use **Backend Configuration** to store your state file. Harness will not sync with the current state when **All Modules** is selected. Instead, Harness simply applies the terragrunt.hcl files.

  :::info
  
   - When **All Modules** is selected, the **Export JSON representation of Terragrunt Plan** option is not supported.
   - Terragrunt Apply and Destroy steps do not support inheriting from a Terragrunt Plan step when **All Modules** is selected in the Terragrunt Plan step's **Module Configuration**.
  
  :::
- **Specific Module**. Harness will use a single terragrunt.hcl file in the folder you specify in **Path**.

The **Path** setting supports [fixed values, runtime inputs, and expressions](https://developer.harness.io/docs/platform/references/runtime-inputs/).

