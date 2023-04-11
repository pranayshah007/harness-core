 Skip the config file name argument.  
shift  
today=`date +%F`  
echo "  
kind: ConfigMap  
apiVersion: v1  
metadata:  
  name: the-map  
data:  
  today: $today  
  altGreeting: "$1"  
  enableRisky: "$2"  
"  
EOF  
cat $MY_PLUGIN_DIR/SillyConfigMapGenerator  
chmod +x $MY_PLUGIN_DIR/SillyConfigMapGenerator  
readlink -f $MY_PLUGIN_DIR/SillyConfigMapGenerator
```

Each plugin is added to its own directory, following this convention:

```bash
$XDG_CONFIG_HOME/kustomize/plugin  
    /${apiVersion}/LOWERCASE(${kind})
```
The default value of `XDG_CONFIG_HOME` is `$HOME/.config`. See [Extending Kustomize](https://kubectl.docs.kubernetes.io/guides/extending_kustomize/) from Kustomize.

In the script example above, you can see that the plugin is added to its own folder following the plugin convention:


```bash
$HOME/K_PLUGINS/kustomize/plugin/myDevOpsTeam/sillyconfigmapgenerator
```
Note the location of the plugin because you will use that location in the Harness Service to indicate where the plugin is located (described below).

Plugins can only be applied to Harness Kubernetes Delegates.
