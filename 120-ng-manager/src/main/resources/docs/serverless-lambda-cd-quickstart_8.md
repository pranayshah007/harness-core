# Installing Serverless on the Delegate

Now we need to edit the YAML to install Serverless when the Delegate pods are created.

1. Open the Delegate YAML in a text editor.
2. Locate the Environment variable `INIT_SCRIPT` in the `StatefulSet` (Legacy Delegate) or `Deployment` (Harness Delegate) object:
	```yaml
	...  
			- name: INIT_SCRIPT  
			value: ""  
	...
	```
1. Replace the value with the following Serverless installation script (the Harness Delegate uses the Red Hat Universal Base Image (UBI)).
	
	Here's an example using microdnf and npm:
	
	```yaml
	...  
        - name: INIT_SCRIPT  
        value: |-  
            #!/bin/bash
            
            # Install Node.js and npm on the Red Hat UBI image using Microdnf
            microdnf install -y nodejs
            
            # Install the Serverless Framework using npm
            npm install -g serverless@2.50.0 
	...
	
	```

	Here's an example using yum and npm:
	
	```yaml
	...  
        - name: INIT_SCRIPT  
        value: |-  
            #!/bin/bash

            # Install Node.js and npm on the Red Hat UBI image
            yum install -y nodejs

            # Install the Serverless Framework using npm
            npm install -g serverless@2.50.0
	...	
	
	```

In cases when the Delegate OS doesn't support `apt` (Red Hat Linux), you can edit this script to install `npm`. The rest of the code should remain the same. If you are using Harness Delegate, the base image is Red Hat UBI.Save the YAML file as **harness-delegate.yml**.
