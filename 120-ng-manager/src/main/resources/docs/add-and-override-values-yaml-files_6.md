## Helm Charts

The typical Helm chart uses the following files:


```bash
chart/              # Helm chart folder  
|-Chart.yaml        # chart definition  
|-requirements.yaml # optional charts to deploy with your chart  
|-values.yaml       # values for the template variables  
|-templates/        # directory containing the template files (Kubernetes manifests)
```

Harness support Helm charts using Helm templating. Harness will evaluate the Helm chart just like Helm. You do not need to install Helm on the Harness Delegate pod/host. Harness manages Helm for you.

Here's a quick video that shows how to add Values YAML files for Kubernetes and Helm Charts. 

<!-- Video:
https://www.youtube.com/watch?v=dVk6-8tfwJc-->
<docvideo src="https://www.youtube.com/watch?v=dVk6-8tfwJc" />

