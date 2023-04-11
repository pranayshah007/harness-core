# Architecture Summary

Harness CD Community Edition has two main components:

* **Harness Manager:** the Harness Manager is where your CD configurations are stored and your pipelines are managed.
  * After you install Harness, you sign up in the Manager at <http://localhost/#/signup>.
  * Pipelines are triggered manually in the Harness Manager or automatically in response to Git events, schedules, new artifacts, and so on.
* **Harness Delegate:** the Harness Delegate is a software service you install in your environment that connects to the Harness Manager and performs tasks using your container orchestration platforms, artifact repositories, etc. 
  * You can install a Delegate inline when setting up connections to your resources or separately as needed. This guide will walk you through setting up a Harness Delegate inline.
