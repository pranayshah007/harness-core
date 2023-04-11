# Specify input variables

While you can simply declare a variable in your script using a Harness expression or string for its value, using Input Variables provides some additional benefits:

* You can more easily identify and manage the Harness expressions used in your script.
* You can template your script.

You can declare the variable using **Name** and **Value** in **Script Input Variables** and then reference the variable in the script just as you would any other variable: `$var_name`.

You can also use expressions in **Value**. For example, if you have an Output Variable from a previous Shell Script step, you can copy it from the executed step **Outputs**.

1. In **Script Input Variables**, you simply select **Expression** and paste the expression in **Value**:
   
   ![](./static/using-shell-scripts-18.png)
2. In the Script, you declare the variable using the **Name** value (in this example, `foo`).
   
   ![picture 3](static/3efd3f47e73c3ca4804bd0e728d8815194ae80c9284ddfe0c11fb07c520b3b0c.png)

At deployment runtime, Harness evaluates the expression and the variable contains its output.
