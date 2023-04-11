# Notes

* **Delete and Traffic Management**: If you are splitting traffic using the **Apply step**, move the **K8s** **Delete** step after the traffic shifting. This will prevent any traffic from being routed to deleted pods before traffic is routed to stable pods.
