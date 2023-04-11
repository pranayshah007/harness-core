# Notes

* Harness does not roll back Canary deployments because your production is not affected during Canary. Canary catches issues before moving to production. Also, you might want to analyze the Canary deployment. The Canary Delete step is useful to perform cleanup when required.
