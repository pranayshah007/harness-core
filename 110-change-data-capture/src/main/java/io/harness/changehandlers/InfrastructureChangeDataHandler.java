/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.utils.YamlPipelineUtils;

import com.mongodb.DBObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfrastructureChangeDataHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    columnValueMapping.put("id", changeEvent.getUuid());

    if (dbObject == null) {
      return columnValueMapping;
    }
    if (dbObject.get(InfrastructureEntityKeys.identifier) != null) {
      columnValueMapping.put("identifier", dbObject.get(InfrastructureEntityKeys.identifier).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(InfrastructureEntityKeys.name).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.accountId) != null) {
      columnValueMapping.put("account_id", dbObject.get(InfrastructureEntityKeys.accountId).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.orgIdentifier) != null) {
      columnValueMapping.put("org_identifier", dbObject.get(InfrastructureEntityKeys.orgIdentifier).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.projectIdentifier) != null) {
      columnValueMapping.put("project_identifier", dbObject.get(InfrastructureEntityKeys.projectIdentifier).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.envIdentifier) != null) {
      columnValueMapping.put("env_identifier", dbObject.get(InfrastructureEntityKeys.envIdentifier).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.createdAt) != null) {
      columnValueMapping.put("created_at", dbObject.get(InfrastructureEntityKeys.createdAt).toString());
    }

    if (dbObject.get(InfrastructureEntityKeys.lastModifiedAt) != null) {
      columnValueMapping.put("last_modified_at", dbObject.get(InfrastructureEntityKeys.lastModifiedAt).toString());
    }
    String type = dbObject.get(InfrastructureEntityKeys.type).toString();
    if (dbObject.get(InfrastructureEntityKeys.type) != null) {
      columnValueMapping.put("type", dbObject.get(InfrastructureEntityKeys.type).toString());
    }
    String yaml = dbObject.get(InfrastructureEntityKeys.yaml).toString();
    InfrastructureConfig config = null;
    Infrastructure infrastructure;
    try {
      config = YamlPipelineUtils.read(yaml, InfrastructureConfig.class);
      infrastructure = config.getInfrastructureDefinitionConfig().getSpec();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create infrastructure config due to " + e.getMessage());
    }
    String region = null;
    String cluster = null;
    String subscription_id = null;
    String namespace = null;
    String resource_group = null;
    switch (type) {
      case "KUBERNETES_DIRECT":
        namespace
        = ((K8SDirectInfrastructure) infrastructure).getNamespace().getValue();
        break;
      case "SSH_WINRM_AZURE":
        subscription_id = ((SshWinRmAzureInfrastructure) infrastructure).getSubscriptionId().getValue();
        resource_group = ((SshWinRmAzureInfrastructure) infrastructure).getResourceGroup().getValue();
        break;
      case "SSH_WINRM_AWS":
        region = ((SshWinRmAwsInfrastructure) infrastructure).getRegion().getValue();
        break;
      case "KUBERNETES_AZURE":
        cluster = ((K8sAzureInfrastructure) infrastructure).getCluster().getValue();
        resource_group = ((K8sAzureInfrastructure) infrastructure).getResourceGroup().getValue();
        subscription_id = ((K8sAzureInfrastructure) infrastructure).getSubscriptionId().getValue();
        namespace = ((K8sAzureInfrastructure) infrastructure).getNamespace().getValue();
        break;
      case "AZURE_WEB_APP":
        subscription_id = ((AzureWebAppInfrastructure) infrastructure).getSubscriptionId().getValue();
        resource_group = ((AzureWebAppInfrastructure) infrastructure).getResourceGroup().getValue();
        break;
      case "KUBERNETES_GCP":
        cluster = ((K8sGcpInfrastructure) infrastructure).getCluster().getValue();
        namespace = ((K8sGcpInfrastructure) infrastructure).getNamespace().getValue();
        break;
      case "SERVERLESS_AWS_LAMBDA":
        region = ((ServerlessAwsLambdaInfrastructure) infrastructure).getRegion().getValue();
        break;
      case "KUBERNETES_AWS":
        cluster = ((K8sAwsInfrastructure) infrastructure).getCluster().getValue();
        namespace = ((K8sAwsInfrastructure) infrastructure).getNamespace().getValue();
        break;
      default:
        break;
    }
    if (region != null) {
      columnValueMapping.put("region", region);
    }
    if (cluster != null) {
      columnValueMapping.put("cluster", cluster);
    }
    if (subscription_id != null) {
      columnValueMapping.put("subscription_id", subscription_id);
    }
    if (namespace != null) {
      columnValueMapping.put("namespace", namespace);
    }
    if (resource_group != null) {
      columnValueMapping.put("resource_group", resource_group);
    }
    return columnValueMapping;
  }

  public boolean shouldDelete() {
    return false;
  }

  @Override
  public Map<String, String> getColumnValueMappingForDelete() {
    Map<String, String> columnValueMapping = new HashMap<>();
    columnValueMapping.put("deleted", "true");
    columnValueMapping.put("deleted_at", String.valueOf(System.currentTimeMillis()));
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }
}
