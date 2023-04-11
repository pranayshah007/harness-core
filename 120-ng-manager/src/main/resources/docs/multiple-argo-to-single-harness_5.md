# Adding new mappings to existing Agent

You can add new mappings to an existing Agent in the Agent's **Mapped Harness Project** settings.

1. In Harness, open an existing Agent.
2. Click **Edit**. In this example, I already have the Argo CD project **alpha** mapped to the Harness Project **GitOps**.
   
   ![](./static/multiple-argo-to-single-harness-73.png)
   
   Let's add a new mapping.

3. In **Mapped Harness Project**, click **Add**.
4. In **Map Projects**, in **Select your Argo Projects to Import**, select the new Argo CD project to map. Do not select a project you have already mapped.
5. Map the new Argo CD project to a Harness Project and click **Import & Continue**.
   Do not re-map an existing mapping. Harness will throw an error.
   
   ![](./static/multiple-argo-to-single-harness-74.png)

6. When the import is complete, click **Finish**.  

Both projects are now mapped.

![](./static/multiple-argo-to-single-harness-75.png)
