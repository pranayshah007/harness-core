<#macro delegateEnv>
        - name: CLIENT_CERTIFICATE_PATH
          value: "/etc/mtls/client.crt"
        - name: CLIENT_CERTIFICATE_KEY_PATH
          value: "/etc/mtls/client.key"
        - name: GRPC_AUTHORITY_MODIFICATION_DISABLED
          value: "true"
</#macro>

<#macro delegateVolume fullDelegateName=delegateName>
      - name: client-certificate
        secret:
          secretName: ${fullDelegateName}-client-certificate
</#macro>

<#macro delegateVolumeMount>
        - name: client-certificate
          mountPath: /etc/mtls
          readOnly: true
</#macro>

<#macro upgraderCfg>
    clientCertificateFilePath: /etc/mtls/client.crt
    clientCertificateKeyFilePath: /etc/mtls/client.key
</#macro>

<#macro upgraderVolume fullDelegateName=delegateName>
            - name: client-certificate
              secret:
                secretName: ${fullDelegateName}-client-certificate
</#macro>

<#macro upgraderVolumeMount>
              - name: client-certificate
                mountPath: /etc/mtls
                readOnly: true
</#macro>

<#macro secret fullDelegateName=delegateName>
apiVersion: v1
kind: Secret
metadata:
  name: ${fullDelegateName}-client-certificate
  namespace: ${delegateNamespace}
type: Opaque
data:
  # Please replace below values with base64 encoded PEM files, or mount an already existing secret instead.
  client.crt: "{CERT}"
  client.key: "{KEY}"
</#macro>
