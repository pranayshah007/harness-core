# Limitations

* Helm subcharts are supported for the following deployment types only.
    - Kubernetes deployments using canary, blue/green, and rolling deployment strategies
    - Native Helm deployments using basic strategy
* Harness Continuous Delivery (CD) captures the parent chart as the deployed instance. Harness Continuous Verification (CV) detects and verifies the parent chart as the deployed instance. CV cannot simultaneously verify all subcharts as deployed instances. 
