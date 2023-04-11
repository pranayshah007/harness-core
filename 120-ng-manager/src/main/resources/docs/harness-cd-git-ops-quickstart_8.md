# Step 5: Perform GitOps

Now that you have everything set up, you can sync the source state with the desired state.

1. In your Application, click **SYNC**.

   ![](./static/harness-cd-git-ops-quickstart-18.png)

2. The Synchronize settings appear.

   ![](./static/harness-cd-git-ops-quickstart-19.png)

3. Here you can change any of the **Sync Policy** options you set in the Application.
4. Click **Synchronize**.
5. You will see the status **Progressing** and then **HEALTHY**.

![](./static/harness-cd-git-ops-quickstart-20.png)

Congratulations! You've performed GitOps with Harness!

Let's look in the cluster to verify the deployment.

```bash
kubectl get pods -n default
```

You will see the example-helm-guestbook Pods running.

```bash
NAME                                      READY   STATUS    RESTARTS   AGE  
...  
example-helm-guestbook-74b6547d8c-74ckv   1/1     Running   0          5m22s  
...
```
Here's what the deployment looks like in GCP.

![](./static/harness-cd-git-ops-quickstart-21.png)
