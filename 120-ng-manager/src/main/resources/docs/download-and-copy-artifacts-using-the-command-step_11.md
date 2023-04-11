# Use a Script

You can run a script on all of the target hosts. This is the same as the [Shell Script](using-shell-scripts.md) step.

1. In **Working Directory**, enter the working directory on the target host(s) from which the Harness Delegate will run the script, such as **/tmp** on Linux and **%TEMP%** on Windows. By default, if **Working Directory** is left empty, the script is executed in the home directory.
2. In **Select script location**, select [Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) or **Inline**.
3. In **Command**, enter your script. For example, this script echoes artifact information using Harness expressions:

	```
	echo "artifacts.primary.tag" <+artifacts.primary.tag>  
	echo "artifacts.primary.tagRegex" <+artifacts.primary.tagRegex>  
	echo "artifacts.primary.identifier" <+artifacts.primary.identifier>  
	echo "artifacts.primary.type" <+artifacts.primary.type>  
	echo "artifacts.primary.primaryArtifact" <+artifacts.primary.primaryArtifact>  
	echo "artifacts.primary.image" <+artifacts.primary.image>  
	echo "artifacts.primary.imagePullSecret" <+artifacts.primary.imagePullSecret>  
	echo "artifacts.primary.label" <+artifacts.primary.label>  
	echo "artifacts.primary.connectorRef" <+artifacts.primary.connectorRef>  
	echo "artifacts.primary.imagePath" <+artifacts.primary.imagePath>
	```

	Here's an example of an executed script command:

	![](./static/download-and-copy-artifacts-using-the-command-step-15.png)
1. Use **Files and Patterns** to tail files and use the results in your script. For example, check logs and see if a process started successfully and, if so, exit the script.  
You specify the file to tail, such as `auth.log`, and the pattern to search (grep filter), such as `198.51.100.1` and then in your script you perform an action when the tail output is matched.
1. For **File to Tail**, enter the name of a file in the Working Directory to tail, such as a log file.
2. For **Pattern to search**, enter the pattern to search for in the file. Harness uses grep on the pattern.

**Deployment Templates:** to run the Script command on the target hosts, add the command after the Fetch Instances step. See [Looping Strategy and target hosts](download-and-copy-artifacts-using-the-command-step.md#looping-strategy-and-target-hosts) below.
