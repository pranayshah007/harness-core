## Command

1. Enter the commands you want to run in the container.

You don't need to create a Kubernetes deployment and service for the container in your script, because Harness handles Kubernetes orchestration.

You also don't need to add `docker run` to your script.

Instead, add the contents of the script you want to execute.

For example, if you would normally run this:

```
docker run --rm -it -v $(pwd):/app -w /app maven:3.6.3-jdk-8 bash -c "..."
```

You can just enter what you would write in `...`.
