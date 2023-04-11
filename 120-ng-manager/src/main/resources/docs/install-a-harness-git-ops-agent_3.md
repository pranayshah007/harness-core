# Review: Using Existing Argo CD Projects

Typically, when you set up a Harness GitOps Agent you install a new Harness GitOps Agent in your target cluster along with other services (Repo server, Redis cache, Application controller).

In some cases, you might already have an Argo CD Project running in your target cluster. In this case, you can select this Project when you set up the Harness GitOps Agent.

You can use an existing Argo CD Project when you already have services deployed to different environments from that Argo CD instance.

If you don't use an existing Argo CD Project, you will create GitOps Applications, Repos, Clusters, etc in Harness. You'll also have to delete these from any existing Argo CD Project in the cluster (if necessary).

In both cases, you will install the Harness GitOps Agent process.

If you use an existing Argo CD instance, then Harness will use the following existing processes in the cluster:

* Repo server
* Redis cache
* Application controller

If you do not use an existing Argo CD instance, then Harness will install the following:

* GitOps agent
* Repo server
* Redis cache
* Application controller

See [Harness GitOps Basics](harness-git-ops-basics.md).
