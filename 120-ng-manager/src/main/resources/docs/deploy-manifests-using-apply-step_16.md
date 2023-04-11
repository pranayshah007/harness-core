# Command flags

Kubernetes command flags are command-line options that can be passed to a Kubernetes command to modify its behavior.

You can add command flags in your Apply step and Harness will run them after the `kubectl apply -f <filename>` command it runs to deploy your manifest.

The availability of specific command flags is based on the version of the kubectl binary that is installed on the Harness delegate performing deployment. For example, `kubectl apply -f <filename> --server-side` is only available on kubectl version 1.22. 

To use command flags, do the following:

1. In the Apply step, click **Advanced**.
2. In **Command Flags**, click **Add**.
3. In **Command Type**, select **Apply**.
4. In Flag, enter the flag in the standard `kubectl` format of `--[flag name]`, such as `--server-side`.

![picture 1](static/a49029b13f887434df1093b299ad179f7eaa6d3a7f8f601f40c34c0f47e41059.png)  


:::note

You cannot use `kubectl` subcommands in the **Command Flags** setting. For example, the subcommand `kubectl apply set-last-applied` will not work.

:::

For more information on command flags, go to [Apply](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#apply) in the Kubernetes documentation.

For more information on installing custom binaries on the delegate, go to [Install a delegate with third-party tool custom binaries](https://developer.harness.io/docs/platform/delegates/install-delegates/install-a-delegate-with-3-rd-party-tool-custom-binaries/).
