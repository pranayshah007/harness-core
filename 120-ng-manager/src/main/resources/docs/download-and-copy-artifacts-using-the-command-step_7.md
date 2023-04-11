## Copy vs download

Let's review the differences between the Copy Artifact/Config and Download commands.

- **Download:** At deployment runtime, the Harness Delegate executes commands on the target host(s) to download the artifact directly to the target host(s).
  The Delegate must have access to the target host(s) and the target host(s) must have network connectivity to the artifact server.
- **Copy:** During deployment runtime, Harness uses the metadata to download the artifact to the Harness Delegate. The Delegate then copies the artifact to the target host(s).

The Delegate must have network connectivity to the artifact server and target hosts.

Here is the difference in how Harness performs a copy and download.

![](./static/download-and-copy-artifacts-using-the-command-step-07.png)
