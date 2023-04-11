# Quotation Marks

The following example puts quotations around whatever string is in the `something` value. This can handle values that could otherwise be interpreted as numbers, or empty values, which would cause an error.


```yaml
{{.Values.something | quote}}
```
You should use single quotes if you are using a value that might contain a YAML-like structure that could cause issues for the YAML parser.
