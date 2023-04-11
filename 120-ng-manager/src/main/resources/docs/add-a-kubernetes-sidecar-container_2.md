# Option: Use Harness Artifacts

You can hardcode the image location in your sidecar manifests or use the the **Artifacts** settings in the Harness Service Definition to connect Harness to an artifact stream (for example, a Docker registry).

When you use **Artifacts**, your sidecar manifest refers to the sidecar artifact you added in **Artifacts** using the expression `<+artifacts.sidecars.[sidecar_identifier].imagePath>`.

The `[sidecar_identifier]` path is the **Sidecar Identifier** you specified when you added the sidecar artifact.

![](./static/add-a-kubernetes-sidecar-container-22.png)

Once your artifact is added, you can see the Id in **Artifacts**. For example, the Id here is **sidecar**.

![](./static/add-a-kubernetes-sidecar-container-23.png)

To add a sidecar artifact, open your Harness stage.

In **Service**, in **Artifacts**, click **Add Sidecar**.

Select an artifact repository type. In this example, we'll use Docker Registry.

Select **Docker Registry**, and click **Continue**.

The **Docker Registry** settings appear.

Select a [Docker Registry Connector](../../../platform/7_Connectors/ref-cloud-providers/docker-registry-connector-settings-reference.md) or create a new one.

Click **Continue**.

In **Sidecar Identifier**, give a name to identify this artifact. As mentioned earlier, this is the name you will use to refer to this artifact in your manifest using the expression `<+artifacts.sidecars.[sidecar_identifier].imagePath>`.

In **Image path**, the name of the artifact you want to deploy, such as **library/nginx**. You can also use a [runtime input](../../../platform/20_References/runtime-inputs.md) (`<+input>`) or Harness variable expression.

In **Tag**, add the Docker tag of the image you want to deploy. If you leave this as `<+input>` you are prompted for the tag at runtime. Harness pulls the available tags, and you simply select one.

Click **Save**.

The artifact is added to **Artifacts**.
