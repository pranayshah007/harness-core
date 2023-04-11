# Reserved Keywords

The word `var` is a reserved word for Input and Output Variable names in the Shell Script step.

If you must use `var`, you can use single quotes and `get()` when referencing the published output variable.

Instead of using `<+test.var>` use `<+test.get('var')>`.

