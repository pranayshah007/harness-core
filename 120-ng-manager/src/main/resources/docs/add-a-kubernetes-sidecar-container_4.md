# Review: Primary and Sidecar Manifest and Values Files

If your stage deploys both primary and sidecar resources, you add one **K8s Manifest** in the **Manifests** section that points to the folder(s) containing the primary and sidecar manifests.

Next, you add one or separate **Values YAML** files for the primary and sidecar resources.

![](./static/add-a-kubernetes-sidecar-container-24.png)

If you are using Harness **Artifacts**, you reference Primary and Sidecar **Artifacts** using different expressions:

* **Primary:** `<+artifact.image>`
* **Sidecar:** `<+artifacts.sidecars.[sidecar_identifier].imagePath>`

For example, here is a single values.yaml for one primary artifact and two sidecars:


```yaml
...  
image1: <+artifact.image>  
dockercfg1: <+artifact.imagePullSecret>  
  
image2: <+artifacts.sidecars.sidecar1.image>  
dockercfg2: <+artifacts.sidecars.sidecar1.imagePullSecret>  
  
image3: <+artifacts.sidecars.sidecar2.image>  
dockercfg3: <+artifacts.sidecars.sidecar2.imagePullSecret>  
...
```

The corresponding manifest would also need entries for image1, image2, and image3.

```yaml
...  
apiVersion: apps/v1  
kind: Deployment  
metadata:  
  name: {{ template "todolist.fullname" . }}  
  namespace: {{ .Values.namespace }}  
  labels:  
    app: {{ template "todolist.name" . }}  
    chart: {{ template "todolist.chart" . }}  
    release: "{{ .Release.Name }}"  
    harness.io/release: {{ .Release.Name }}  
    heritage: {{ .Release.Service }}  
spec:  
  replicas: {{ .Values.replicaCount }}  
  selector:  
    matchLabels:  
      app: {{ template "todolist.name" . }}  
      release: {{ .Release.Name }}  
  template:  
    metadata:  
      labels:  
        app: {{ template "todolist.name" . }}  
        release: {{ .Release.Name }}  
        harness.io/release: {{ .Release.Name }}  
    spec:  
      {{- if .Values.dockercfg1}}  
      imagePullSecrets:  
      - name: {{.Values.name}}-dockercfg1  
      - name: {{.Values.name}}-dockercfg2  
      - name: {{.Values.name}}-dockercfg3  
      {{- end}}  
      containers:  
        - name: {{ .Chart.Name }}-1  
          image: {{.Values.image1}}  
          imagePullPolicy: {{ .Values.pullPolicy }}  
          {{- if or .Values.env.config .Values.env.secrets}}  
          envFrom:  
          {{- if .Values.env.config}}  
          - configMapRef:  
              name: {{.Values.name}}  
          {{- end}}  
          {{- if .Values.env.secrets}}  
          - secretRef:  
              name: {{.Values.name}}  
          {{- end}}  
          {{- end}}  
        - name: {{ .Chart.Name }}-2  
          image: {{.Values.image2}}  
          imagePullPolicy: {{ .Values.pullPolicy }}  
          {{- if or .Values.env.config .Values.env.secrets}}  
          envFrom:  
          {{- if .Values.env.config}}  
          - configMapRef:  
              name: {{.Values.name}}  
          {{- end}}  
          {{- if .Values.env.secrets}}  
          - secretRef:  
              name: {{.Values.name}}  
          {{- end}}  
          {{- end}}  
        - name: {{ .Chart.Name }}-3  
          image: {{.Values.image3}}  
          imagePullPolicy: {{ .Values.pullPolicy }}  
          {{- if or .Values.env.config .Values.env.secrets}}  
          envFrom:  
          {{- if .Values.env.config}}  
          - configMapRef:  
              name: {{.Values.name}}  
          {{- end}}  
          {{- if .Values.env.secrets}}  
          - secretRef:  
              name: {{.Values.name}}  
          {{- end}}  
          {{- end}}
```