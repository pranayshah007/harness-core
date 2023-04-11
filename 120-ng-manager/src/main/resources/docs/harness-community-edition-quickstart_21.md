## Harness Docker Delegate and the M1 Processor

This quickstart uses the Harness Kubernetes Delegate. If you decide to use the Harness Docker Delegate and your laptop uses an M1 processor, edit the Docker Delegate YAML `cpus` to use `1` before installation:

```yaml
version: "3.7"  
services:  
  harness-ng-delegate:  
    restart: unless-stopped  
    deploy:  
      resources:  
        limits:  
          cpus: "1"  
          memory: 2048M  
...
```

This is a temporary change. In the next release of Harness CD Community Edition, the Docker Delegate YAML will use `cpus: "1"` by default.
