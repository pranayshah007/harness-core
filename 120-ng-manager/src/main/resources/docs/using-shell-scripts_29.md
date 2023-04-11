## Published variables not available

This error happens when you are publishing output via the **Script Output Variables** setting and your Shell Script step exits early from its script.

There are many errors that can result from this situation. For example, you might see an error such as:

```bash
FileNotFoundException inside shell script execution task
```

If you exit from the script (`exit 0`), values for the context cannot be read.

Instead, if you publish output variables in your Shell Script command, structure your script with `if...else` blocks to ensure it always runs to the end of the script.
