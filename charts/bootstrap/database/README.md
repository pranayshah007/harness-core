# database

![Version: 0.8.0](https://img.shields.io/badge/Version-0.8.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 1.0.0](https://img.shields.io/badge/AppVersion-1.0.0-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| file://./redis | redis | 0.8.x |
| file://./timescaledb | timescaledb | 0.8.x |
| https://charts.bitnami.com/bitnami | clickhouse | 2.1.0 |
| https://charts.bitnami.com/bitnami | minio | 11.9.1 |
| https://charts.bitnami.com/bitnami | mongodb | 13.1.2 |
| https://charts.bitnami.com/bitnami | postgresql | 12.4.2 |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| clickhouse.defaultConfigurationOverrides | string | `"<clickhouse>\n  <!-- Macros -->\n  <macros>\n    <shard from_env=\"CLICKHOUSE_SHARD_ID\"></shard>\n    <replica from_env=\"CLICKHOUSE_REPLICA_ID\"></replica>\n    <layer>{{ include \"common.names.fullname\" . }}</layer>\n  </macros>\n  <!-- Log Level -->\n  <logger>\n    <level>{{ .Values.logLevel }}</level>\n  </logger>\n  {{- if or (ne (int .Values.shards) 1) (ne (int .Values.replicaCount) 1)}}\n  <!-- Cluster configuration - Any update of the shards and replicas requires helm upgrade -->\n  <remote_servers>\n    <default>\n      {{- $shards := $.Values.shards | int }}\n      {{- range $shard, $e := until $shards }}\n      <shard>\n          <internal_replication>false</internal_replication>\n          {{- $replicas := $.Values.replicaCount | int }}\n          {{- range $i, $_e := until $replicas }}\n          <replica>\n              <host>{{ printf \"%s-shard%d-%d.%s.%s.svc.%s\" (include \"common.names.fullname\" $ ) $shard $i (include \"clickhouse.headlessServiceName\" $) (include \"common.names.namespace\" $) $.Values.clusterDomain }}</host>\n              <port>{{ $.Values.service.ports.tcp }}</port>\n          </replica>\n          {{- end }}\n      </shard>\n      {{- end }}\n    </default>\n  </remote_servers>\n  {{- end }}\n  {{- if .Values.keeper.enabled }}\n  <!-- keeper configuration -->\n  <keeper_server>\n    {{/*ClickHouse keeper configuration using the helm chart */}}\n    <tcp_port>{{ $.Values.containerPorts.keeper }}</tcp_port>\n    {{- if .Values.tls.enabled }}\n    <tcp_port_secure>{{ $.Values.containerPorts.keeperSecure }}</tcp_port_secure>\n    {{- end }}\n    <server_id from_env=\"KEEPER_SERVER_ID\"></server_id>\n    <log_storage_path>/bitnami/clickhouse/keeper/coordination/log</log_storage_path>\n    <snapshot_storage_path>/bitnami/clickhouse/keeper/coordination/snapshots</snapshot_storage_path>\n\n    <coordination_settings>\n        <operation_timeout_ms>10000</operation_timeout_ms>\n        <session_timeout_ms>30000</session_timeout_ms>\n        <raft_logs_level>trace</raft_logs_level>\n    </coordination_settings>\n\n    <raft_configuration>\n    {{- $nodes := .Values.replicaCount | int }}\n    {{- range $node, $e := until $nodes }}\n    <server>\n      <id>{{ $node | int }}</id>\n      <hostname from_env=\"{{ printf \"KEEPER_NODE_%d\" $node }}\"></hostname>\n      <port>{{ $.Values.service.ports.keeperInter }}</port>\n    </server>\n    {{- end }}\n    </raft_configuration>\n  </keeper_server>\n  {{- end }}\n  {{- if or .Values.keeper.enabled .Values.zookeeper.enabled .Values.externalZookeeper.servers }}\n  <!-- Zookeeper configuration -->\n  <zookeeper>\n    {{- if or .Values.keeper.enabled }}\n    {{- $nodes := .Values.replicaCount | int }}\n    {{- range $node, $e := until $nodes }}\n    <node>\n      <host from_env=\"{{ printf \"KEEPER_NODE_%d\" $node }}\"></host>\n      <port>{{ $.Values.service.ports.keeper }}</port>\n    </node>\n    {{- end }}\n    {{- else if .Values.zookeeper.enabled }}\n    {{/* Zookeeper configuration using the helm chart */}}\n    {{- $nodes := .Values.zookeeper.replicaCount | int }}\n    {{- range $node, $e := until $nodes }}\n    <node>\n      <host from_env=\"{{ printf \"KEEPER_NODE_%d\" $node }}\"></host>\n      <port>{{ $.Values.zookeeper.service.ports.client }}</port>\n    </node>\n    {{- end }}\n    {{- else if .Values.externalZookeeper.servers }}\n    {{/* Zookeeper configuration using an external instance */}}\n    {{- range $node :=.Values.externalZookeeper.servers }}\n    <node>\n      <host>{{ $node }}</host>\n      <port>{{ $.Values.externalZookeeper.port }}</port>\n    </node>\n    {{- end }}\n    {{- end }}\n  </zookeeper>\n  {{- end }}\n  {{- if .Values.tls.enabled }}\n  <!-- TLS configuration -->\n  <tcp_port_secure from_env=\"CLICKHOUSE_TCP_SECURE_PORT\"></tcp_port_secure>\n  <openSSL>\n      <server>\n          {{- $certFileName := default \"tls.crt\" .Values.tls.certFilename }}\n          {{- $keyFileName := default \"tls.key\" .Values.tls.certKeyFilename }}\n          <certificateFile>/bitnami/clickhouse/certs/{{$certFileName}}</certificateFile>\n          <privateKeyFile>/bitnami/clickhouse/certs/{{$keyFileName}}</privateKeyFile>\n          <verificationMode>none</verificationMode>\n          <cacheSessions>true</cacheSessions>\n          <disableProtocols>sslv2,sslv3</disableProtocols>\n          <preferServerCiphers>true</preferServerCiphers>\n          {{- if or .Values.tls.autoGenerated .Values.tls.certCAFilename }}\n          {{- $caFileName := default \"ca.crt\" .Values.tls.certFilename }}\n          <caConfig>/bitnami/clickhouse/certs/{{$caFileName}}</caConfig>\n          {{- else }}\n          <loadDefaultCAFile>true</loadDefaultCAFile>\n          {{- end }}\n      </server>\n      <client>\n          <loadDefaultCAFile>true</loadDefaultCAFile>\n          <cacheSessions>true</cacheSessions>\n          <disableProtocols>sslv2,sslv3</disableProtocols>\n          <preferServerCiphers>true</preferServerCiphers>\n          <verificationMode>none</verificationMode>\n          <invalidCertificateHandler>\n              <name>AcceptCertificateHandler</name>\n          </invalidCertificateHandler>\n      </client>\n  </openSSL>\n  {{- end }}\n  {{- if .Values.metrics.enabled }}\n  <!-- Prometheus metrics -->\n  <prometheus>\n      <endpoint>/metrics</endpoint>\n      <port from_env=\"CLICKHOUSE_METRICS_PORT\"></port>\n      <metrics>true</metrics>\n      <events>true</events>\n      <asynchronous_metrics>true</asynchronous_metrics>\n  </prometheus>\n  {{- end }}\n</clickhouse>\n"` |  |
| clickhouse.fullnameOverride | string | `"clickhouse"` |  |
| clickhouse.image.tag | string | `"23.3.4-debian-11-r0"` |  |
| clickhouse.persistence.size | string | `"1Ti"` |  |
| clickhouse.podLabels.app | string | `"clickhouse"` |  |
| clickhouse.replicaCount | int | `1` |  |
| clickhouse.resources.limits.memory | string | `"8Gi"` |  |
| clickhouse.resources.requests.cpu | int | `6` |  |
| clickhouse.resources.requests.memory | string | `"8Gi"` |  |
| clickhouse.shards | int | `1` |  |
| clickhouse.zookeeper.enabled | bool | `false` |  |
| clickhouse.zookeeper.fullnameOverride | string | `"clickhouse-zookeeper"` |  |
| clickhouse.zookeeper.replicaCount | int | `1` |  |
| global.database.clickhouse.enabled | bool | `false` |  |
| global.database.minio.installed | bool | `true` |  |
| global.database.mongodb.installed | bool | `true` |  |
| global.database.postgresql.installed | bool | `true` |  |
| global.database.redis.installed | bool | `true` |  |
| global.database.timescaledb.installed | bool | `true` |  |
| minio.auth.existingSecret | string | `"minio"` |  |
| minio.defaultBuckets | string | `"logs"` |  |
| minio.fullnameOverride | string | `"minio"` |  |
| minio.image.tag | string | `"2023.5.18-debian-11-r2"` |  |
| minio.mode | string | `"standalone"` |  |
| minio.persistence.size | string | `"200Gi"` |  |
| mongodb | object | `{"arbiter":{"enabled":true},"architecture":"replicaset","auth":{"existingSecret":"mongodb-replicaset-chart","rootUser":"admin"},"fullnameOverride":"mongodb-replicaset-chart","image":{"registry":"docker.io","repository":"harness/mongo","tag":"4.4.22"},"persistence":{"size":"200Gi"},"podLabels":{"app":"mongodb-replicaset"},"replicaCount":3,"resources":{"limits":{"cpu":4,"memory":"8192Mi"},"requests":{"cpu":4,"memory":"8192Mi"}},"service":{"nameOverride":"mongodb-replicaset-chart"}}` | configurations for mongodb |
| postgresql.auth.database | string | `"overops"` |  |
| postgresql.auth.existingSecret | string | `"postgres"` |  |
| postgresql.commonLabels.app | string | `"postgres"` |  |
| postgresql.fullnameOverride | string | `"postgres"` |  |
| postgresql.image.digest | string | `""` |  |
| postgresql.image.registry | string | `"docker.io"` |  |
| postgresql.image.repository | string | `"bitnami/postgresql"` |  |
| postgresql.image.tag | string | `"14.8.0-debian-11-r17"` |  |
| postgresql.primary.persistence.size | string | `"200Gi"` |  |
| postgresql.primary.resources.limits.cpu | int | `4` |  |
| postgresql.primary.resources.limits.memory | string | `"8192Mi"` |  |
| postgresql.primary.resources.requests.cpu | int | `4` |  |
| postgresql.primary.resources.requests.memory | string | `"8192Mi"` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
