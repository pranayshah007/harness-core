### Rollback Logs

Here's the log activity from a rollback with the timestamps removed:

```
Sending request for stopping deployment slot - [stage]  
Operation - [Stop Slot] was success  
Request sent successfully  
  
Start updating Container settings for slot - [stage]  
Start cleaning existing container settings  
  
Current state for deployment slot is - [Stopped]  
Deployment slot stopped successfully  
  
Start updating application configurations for slot - [stage]  
Deployment slot configuration updated successfully  
  
Existing container settings deleted successfully  
Start cleaning existing image settings  
  
Existing image settings deleted successfully  
Start updating Container settings:   
[[DOCKER_REGISTRY_SERVER_URL]]  
  
Container settings updated successfully  
Start updating container image and tag:   
[library/nginx:1.19-alpine-perl], web app hosting OS [LINUX]  
  
Image and tag updated successfully for slot [stage]  
Deployment slot container settings updated successfully  
  
Sending request for starting deployment slot - [stage]  
Operation - [Start Slot] was success  
Request sent successfully  
  
Sending request to shift [0.00] traffic to deployment slot: [stage]  
  
Current state for deployment slot is - [Running]  
Deployment slot started successfully  
  
Traffic percentage updated successfully  
  
The following task - [SLOT_ROLLBACK] completed successfully
```
