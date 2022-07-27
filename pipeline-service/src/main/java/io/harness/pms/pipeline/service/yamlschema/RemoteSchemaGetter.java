/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.encryption.Scope;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.service.yamlschema.exception.YamlSchemaCacheException;
import io.harness.utils.RetryUtils;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.schema.client.YamlSchemaClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Call;

@Slf4j
public class RemoteSchemaGetter implements SchemaGetter {
  private final YamlSchemaClient schemaClient;
  private final ModuleType moduleType;
  private final String accountIdentifier;

  public RemoteSchemaGetter(YamlSchemaClient schemaClient, ModuleType moduleType, String accountIdentifier) {
    this.schemaClient = schemaClient;
    this.moduleType = moduleType;
    this.accountIdentifier = accountIdentifier;
  }

  @Override
  public List<PartialSchemaDTO> getSchema(List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    try {
      Call<ResponseDTO<List<PartialSchemaDTO>>> call = schemaClient.get(accountIdentifier, null, null, null,
          YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(yamlSchemaWithDetailsList).build());

      RetryPolicy<Object> retryPolicy = getRetryPolicy(moduleType);
      return Failsafe.with(retryPolicy).get(() -> SafeHttpCall.execute(call.clone())).getData();
    } catch (Exception e) {
      log.error(format("[PMS] Unable to get %s schema information", moduleType.name()), e.getCause());
    }
    return null;
  }

  private RetryPolicy<Object> getRetryPolicy(ModuleType moduleType) {
    return RetryUtils.getRetryPolicy(format("[PMS] [Retrying] Error while calling %s service", moduleType.name()),
        format("[PMS] Error while calling %s service", moduleType.name()), ImmutableList.of(Exception.class),
        Duration.ofMillis(600), 3, log);
  }

  @Override
  public YamlSchemaDetailsWrapper getSchemaDetails() {
    try {
      Call<ResponseDTO<YamlSchemaDetailsWrapper>> call =
          schemaClient.getSchemaDetails(accountIdentifier, null, null, null);

      RetryPolicy<Object> retryPolicy = getRetryPolicy(moduleType);
      return Failsafe.with(retryPolicy).get(() -> SafeHttpCall.execute(call.clone())).getData();
    } catch (Exception e) {
      throw new YamlSchemaCacheException(
          format("[PMS] Unable to get %s schema information", moduleType.name()), e.getCause());
    }
  }

  @Override
  public JsonNode fetchStepYamlSchema(String orgIdentifier, String projectIdentifier, Scope scope,
      EntityType entityType, String yamlGroup, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    try {
      Call<ResponseDTO<JsonNode>> call =
          schemaClient.getStepSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope, entityType, yamlGroup,
              YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(yamlSchemaWithDetailsList).build());
      RetryPolicy<Object> retryPolicy = getRetryPolicy(entityType.getEntityProduct());
      return Failsafe.with(retryPolicy).get(() -> SafeHttpCall.execute(call.clone())).getData();

    } catch (Exception e) {
      throw new YamlSchemaCacheException(
          format("[PMS] Unable to get %s %s schema information", entityType.getYamlName(), yamlGroup), e.getCause());
    }
  }
}
