### Inline variable secrets

If you are entering secrets (for credentials, etc.), use Harness secret references in the value of the variable:


```
secrets_encryption_kms_key = "<+secrets.getValue("org.kms_key")>"
```

See [Add Text Secrets](../../../platform/6_Security/2-add-use-text-secrets.md).
