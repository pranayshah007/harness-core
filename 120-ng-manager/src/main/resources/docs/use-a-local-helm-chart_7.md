### Logs

There is a slight difference in the logs for local and remote Helm charts. For example, if Harness doesn't find the chart in the local Delegate disk at the time of first deployment, the logs include `Did not find the chart and version in local repo`:

![](./static/use-a-local-helm-chart-01.png)

When Harness finds the charts it displays the message `Found the chart at local repo at path`.
