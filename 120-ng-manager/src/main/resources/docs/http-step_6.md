# Assertion

Assertion is used to validate the incoming response. For example, if you wanted to check the health of an HTTP connection, use the assertion `<+httpResponseCode> == 200`.

The expression `<+httpResponseCode> == 200` will evaluate to true if the HTTP call returns a 200 code.

Expressions can use the following aliases to refer to the HTTP responses, URL, and method.

* `<+httpResponseCode>`
* `<+httpResponseBody>`
