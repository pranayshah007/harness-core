# Sync the ApplicationSet to create the applications

A Sync brings the live state to its desired state by applying the declarative description. The guestbook application in the ApplicationSet will be created in the two target clusters. After the sync, the resources will look like this in Harness:

![](./static/harness-git-ops-application-set-tutorial-40.png)

When you click **Sync**, Harness will use the ApplicationSet to create the new Harness Applications for the dev and prod clusters.

1. In the GitOps Application, click **SYNC**.
   
   ![](./static/harness-git-ops-application-set-tutorial-41.png)

2. In the Sync settings, click **Synchronize**. Synchronization will take a minute.

In the git-generator-files-discovery Application **Resource View**, you can see the ApplicationSet and new Applications:

![](./static/harness-git-ops-application-set-tutorial-42.png)

Congratulations! Now you have a working ApplicationSet in Harness deploying an application to two target clusters.

Next, we'll create a PR Pipeline to change the application in just one of the target clusters.
