### Basic

The Looping Strategy for the Basic deployment simply repeats the deployment on all the target hosts.

```yaml
repeat:  
  items: <+stage.output.hosts>
```
