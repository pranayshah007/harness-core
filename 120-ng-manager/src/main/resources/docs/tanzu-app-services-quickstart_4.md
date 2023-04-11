# Install Cloud Foundry Command Line Interface (CF CLI) on your Harness delegate

After the delegate pods are created, you must edit your Harness delegate YAML to install CF CLI v7, `autoscaler`, and `Create-Service-Push` plugins.

1. Open the `delegate.yaml` in a text editor.
2. Locate the environment variable `INIT_SCRIPT` in the `Deployment` object.
   ```
   - name: INIT_SCRIPT  
   value: ""  
   ```
3. Replace `value: ""` with the following script to install CF CLI, `autoscaler`, and `Create-Service-Push` plugins.
   
   :::info
   Harness delegate uses Red Hat based distributions like Red Hat Enterprise Linux (RHEL) or Red Hat Universal Base Image (UBI). Hence, we recommend that you use `microdnf` commands to install CF CLI on your delegate. If you are using a package manager in Debian based distributions like Ubuntu, use `apt-get` commands to install CF CLI on your delegate.
   :::

   :::info
   Make sure to use your API token for pivnet login in the following script.
   :::

```mdx-code-block
import Tabs from '@theme/Tabs';   
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
    <TabItem value="microdnf" label="microdnf" default>
```

   ```
   - name: INIT_SCRIPT  
   value: |
    # update package manager, install necessary packages, and install CF CLI v7
    microdnf update
    microdnf install yum
    microdnf install --nodocs unzip yum-utils
    microdnf install -y yum-utils
    echo y | yum install wget
    wget -O /etc/yum.repos.d/cloudfoundry-cli.repo https://packages.cloudfoundry.org/fedora/cloudfoundry-cli.repo
    echo y | yum install cf7-cli -y

    # autoscaler plugin
    # download and install pivnet
    wget -O pivnet https://github.com/pivotal-cf/pivnet-cli/releases/download/v0.0.55/pivnet-linux-amd64-0.0.55 && chmod +x pivnet && mv pivnet /usr/local/bin;
    pivnet login --api-token=<replace with api token>

    # download and install autoscaler plugin by pivnet
    pivnet download-product-files --product-slug='pcf-app-autoscaler' --release-version='2.0.295' --product-file-id=912441
    cf install-plugin -f autoscaler-for-pcf-cliplugin-linux64-binary-2.0.295

    # install Create-Service-Push plugin from community
    cf install-plugin -r CF-Community "Create-Service-Push"

    # verify cf version
    cf --version

    # verify plugins
    cf plugins
   ```

```mdx-code-block
</TabItem>
<TabItem value="apt-get" label="apt-get">
```
   
   ```
   - name: INIT_SCRIPT  
   value: |
    # update package manager, install necessary packages, and install CF CLI v7
    apt-get install wget
    wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | apt-key add -
    echo "deb https://packages.cloudfoundry.org/debian stable main" | tee /etc/apt/sources.list.d/cloudfoundry-cli.list
    apt-get update
    apt-get install cf7-cli

    # autoscaler plugin
    # download and install pivnet
    wget -O pivnet https://github.com/pivotal-cf/pivnet-cli/releases/download/v0.0.55/pivnet-linux-amd64-0.0.55 && chmod +x pivnet && mv pivnet /usr/local/bin;
    pivnet login --api-token=<replace with api token>

    # download and install autoscaler plugin by pivnet
    pivnet download-product-files --product-slug='pcf-app-autoscaler' --release-version='2.0.295' --product-file-id=912441
    cf install-plugin -f autoscaler-for-pcf-cliplugin-linux64-binary-2.0.295

    # install Create-Service-Push plugin from community
    cf install-plugin -r CF-Community "Create-Service-Push"

    # verify cf version
    cf --version

    # verify plugins
    cf plugins
   ```
  
```mdx-code-block
</TabItem>    
</Tabs>
```
   
4. Apply the profile to the delegate profile and check the logs.

   The output for `cf --version` is `cf version 7.2.0+be4a5ce2b.2020-12-10`.

   Here is the output for `cf plugins`.
   
   ```
   App Autoscaler        2.0.295   autoscaling-apps              Displays apps bound to the autoscaler
   App Autoscaler        2.0.295   autoscaling-events            Displays previous autoscaling events for the app
   App Autoscaler        2.0.295   autoscaling-rules             Displays rules for an autoscaled app
   App Autoscaler        2.0.295   autoscaling-slcs              Displays scheduled limit changes for the app
   App Autoscaler        2.0.295   configure-autoscaling         Configures autoscaling using a manifest file
   App Autoscaler        2.0.295   create-autoscaling-rule       Create rule for an autoscaled app
   App Autoscaler        2.0.295   create-autoscaling-slc        Create scheduled instance limit change for an autoscaled app
   App Autoscaler        2.0.295   delete-autoscaling-rule       Delete rule for an autoscaled app
   App Autoscaler        2.0.295   delete-autoscaling-rules      Delete all rules for an autoscaled app
   App Autoscaler        2.0.295   delete-autoscaling-slc        Delete scheduled limit change for an autoscaled app
   App Autoscaler        2.0.295   disable-autoscaling           Disables autoscaling for the app
   App Autoscaler        2.0.295   enable-autoscaling            Enables autoscaling for the app
   App Autoscaler        2.0.295   update-autoscaling-limits     Updates autoscaling instance limits for the app
   Create-Service-Push   1.3.2     create-service-push, cspush   Works in the same manner as cf push, except that it will create services defined in a services-manifest.yml file first before performing a cf push.
   ``` 
:::note
The CF Command script does not require `cf login`. Harness logs in using the credentials in the TAS cloud provider set up in the infrastructure definition for the workflow executing the CF Command.
:::
