# Payloads and Responses

Harness defaults to using JSON resource representation both for payloads and responses. Some cases may exist where other representations are required e.g. YAML. the Content-Type header should be used.

- JSON attribute names should be snake_case
- Datetimes should be int64 Unix timestamp 
