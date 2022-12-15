/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.ARTIFACT_JSON_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ARTIFACT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_JSON_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ASSIGNMENT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_JSON_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.REST_CLIENT_RESPONSE_TIMEOUT;
import static io.harness.azure.model.AzureConstants.VERSION_ID_BLANK_VALIDATION_MSG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.client.AzureBlueprintRestClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.blueprint.Blueprint;
import io.harness.azure.model.blueprint.PublishedBlueprint;
import io.harness.azure.model.blueprint.PublishedBlueprintList;
import io.harness.azure.model.blueprint.artifact.Artifact;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.AssignmentList;
import io.harness.azure.model.blueprint.assignment.WhoIsBlueprintContract;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentOperation;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentOperationList;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.exception.runtime.azure.AzureBPDeploymentException;
import io.harness.exception.runtime.azure.AzureClientRuntimeException;
import io.harness.serializer.JsonUtils;

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
public class AzureBlueprintClientImpl extends AzureClient implements AzureBlueprintClient {
  @Override
  public Blueprint createOrUpdateBlueprint(final AzureConfig azureConfig, final String resourceScope,
      final String blueprintName, final String blueprintJSON) {
    return createOrUpdateBlueprintWithServiceResponseAsync(azureConfig, resourceScope, blueprintName, blueprintJSON)
        .block(REST_CLIENT_RESPONSE_TIMEOUT);
  }

  private Mono<Blueprint> createOrUpdateBlueprintWithServiceResponseAsync(final AzureConfig azureConfig,
      final String resourceScope, final String blueprintName, final String blueprintJSON) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintJSON)) {
      throw new AzureBPDeploymentException(BLUEPRINT_JSON_BLANK_VALIDATION_MSG);
    }
    if (isBlank(blueprintName)) {
      throw new AzureBPDeploymentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }

    JsonNode blueprintObj = JsonUtils.readTree(blueprintJSON);

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .createOrUpdateBlueprint(getAzureBearerAuthToken(azureConfig), resourceScope,
                                  blueprintName, blueprintObj, AzureBlueprintRestClient.APP_VERSION),
        Blueprint.class);
  }

  @Override
  public Blueprint getBlueprint(final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    try {
      return getBlueprintWithServiceResponseAsync(azureConfig, resourceScope, blueprintName)
          .block(REST_CLIENT_RESPONSE_TIMEOUT);
    } catch (AzureClientRuntimeException e) {
      log.warn("Failed to fetch Blueprint", e);
      return null;
    }
  }

  private Mono<Blueprint> getBlueprintWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new AzureBPDeploymentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .getBlueprint(getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName,
                                  AzureBlueprintRestClient.APP_VERSION),
        Blueprint.class);
  }

  @Override
  public Artifact createOrUpdateArtifact(
      AzureConfig azureConfig, String resourceScope, String blueprintName, String artifactName, String artifactJSON) {
    return createOrUpdateArtifactWithServiceResponseAsync(
        azureConfig, resourceScope, blueprintName, artifactName, artifactJSON)
        .block(REST_CLIENT_RESPONSE_TIMEOUT);
  }

  private Mono<Artifact> createOrUpdateArtifactWithServiceResponseAsync(final AzureConfig azureConfig,
      final String resourceScope, final String blueprintName, final String artifactName, final String artifactJSON) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new AzureBPDeploymentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(artifactJSON)) {
      throw new AzureBPDeploymentException(ARTIFACT_JSON_BLANK_VALIDATION_MSG);
    }
    if (isBlank(artifactName)) {
      throw new AzureBPDeploymentException(ARTIFACT_NAME_BLANK_VALIDATION_MSG);
    }

    JsonNode artifactObj = JsonUtils.readTree(artifactJSON);

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .createOrUpdateArtifact(getAzureBearerAuthToken(azureConfig), resourceScope,
                                  blueprintName, artifactName, artifactObj, AzureBlueprintRestClient.APP_VERSION),
        Artifact.class);
  }

  @Override
  public PublishedBlueprint publishBlueprintDefinition(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    return publishBlueprintDefinitionWithServiceResponseAsync(azureConfig, resourceScope, blueprintName, versionId)
        .block(REST_CLIENT_RESPONSE_TIMEOUT);
  }

  private Mono<PublishedBlueprint> publishBlueprintDefinitionWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new AzureBPDeploymentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(versionId)) {
      throw new AzureBPDeploymentException(VERSION_ID_BLANK_VALIDATION_MSG);
    }

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .publishBlueprintDefinition(getAzureBearerAuthToken(azureConfig), resourceScope,
                                  blueprintName, versionId, AzureBlueprintRestClient.APP_VERSION),
        PublishedBlueprint.class);
  }

  @Override
  public PublishedBlueprint getPublishedBlueprintVersion(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    try {
      return getPublishedBlueprintVersionWithServiceResponseAsync(azureConfig, resourceScope, blueprintName, versionId)
          .block(REST_CLIENT_RESPONSE_TIMEOUT);
    } catch (AzureClientRuntimeException e) {
      log.warn("Failed to fetch Published Blueprint", e);
      return null;
    }
  }

  private Mono<PublishedBlueprint> getPublishedBlueprintVersionWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new AzureBPDeploymentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(versionId)) {
      throw new AzureBPDeploymentException(VERSION_ID_BLANK_VALIDATION_MSG);
    }

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .getPublishedBlueprintVersion(getAzureBearerAuthToken(azureConfig), resourceScope,
                                  blueprintName, versionId, AzureBlueprintRestClient.APP_VERSION),
        PublishedBlueprint.class);
  }

  @Override
  public PagedFlux<PublishedBlueprint> listPublishedBlueprintVersions(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    return new PagedFlux(
        ()
            -> listPublishedBlueprintVersionsSinglePageAsync(azureConfig, resourceScope, blueprintName),
        nextLink -> listPublishedBlueprintVersionsNextSinglePageAsync(azureConfig, String.valueOf(nextLink)));
  }

  public Mono<PagedResponse<PublishedBlueprint>> listPublishedBlueprintVersionsSinglePageAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    if (isBlank(resourceScope)) {
      return Mono.error(new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG));
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      return Mono.error(new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope)));
    }
    if (isBlank(blueprintName)) {
      return Mono.error(new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG));
    }

    return executePagedRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                                   .listPublishedBlueprintVersions(getAzureBearerAuthToken(azureConfig), resourceScope,
                                       blueprintName, AzureBlueprintRestClient.APP_VERSION),
        PublishedBlueprintList.class);
  }

  public Mono<PagedResponse<PublishedBlueprint>> listPublishedBlueprintVersionsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (isBlank(nextPageLink)) {
      return Mono.empty();
    }

    return executePagedRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                                   .listPublishedBlueprintVersionsNextPage(getAzureBearerAuthToken(azureConfig),
                                       nextPageLink, AzureBlueprintRestClient.APP_VERSION),
        PublishedBlueprintList.class);
  }

  @Override
  public Assignment beginCreateOrUpdateAssignment(final AzureConfig azureConfig, final String resourceScope,
      final String assignmentName, final String assignmentJSON) {
    return beginCreateOrUpdateAssignmentWithServiceResponseAsync(
        azureConfig, resourceScope, assignmentName, assignmentJSON)
        .block();
  }

  private Mono<Assignment> beginCreateOrUpdateAssignmentWithServiceResponseAsync(final AzureConfig azureConfig,
      final String resourceScope, final String assignmentName, final String assignmentJSON) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new AzureBPDeploymentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(assignmentJSON)) {
      throw new AzureBPDeploymentException(ASSIGNMENT_JSON_BLANK_VALIDATION_MSG);
    }

    JsonNode assignmentObj = JsonUtils.readTree(assignmentJSON);

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .beginCreateOrUpdateAssignment(getAzureBearerAuthToken(azureConfig), resourceScope,
                                  assignmentName, assignmentObj, AzureBlueprintRestClient.APP_VERSION),
        Assignment.class);
  }

  @Override
  public Assignment getAssignment(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    try {
      return getAssignmentWithServiceResponseAsync(azureConfig, resourceScope, assignmentName).block();
    } catch (AzureClientRuntimeException e) {
      log.warn("Failed to fetch Blueprint Assignment", e);
      return null;
    }
  }

  private Mono<Assignment> getAssignmentWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new AzureBPDeploymentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .getAssignment(getAzureBearerAuthToken(azureConfig), resourceScope, assignmentName,
                                  AzureBlueprintRestClient.APP_VERSION),
        Assignment.class);
  }

  @Override
  public PagedFlux<Assignment> listAssignments(final AzureConfig azureConfig, final String resourceScope) {
    return new PagedFlux(()
                             -> listBlueprintAssignmentsSinglePageAsync(azureConfig, resourceScope),
        nextLink -> listBlueprintAssignmentsNextSinglePageAsync(azureConfig, String.valueOf(nextLink)));
  }

  public Mono<PagedResponse<Assignment>> listBlueprintAssignmentsSinglePageAsync(
      final AzureConfig azureConfig, final String resourceScope) {
    if (isBlank(resourceScope)) {
      return Mono.error(new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG));
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      return Mono.error(new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope)));
    }

    return executePagedRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                                   .listBlueprintAssignments(getAzureBearerAuthToken(azureConfig), resourceScope,
                                       AzureBlueprintRestClient.APP_VERSION),
        AssignmentList.class);
  }

  public Mono<PagedResponse<Assignment>> listBlueprintAssignmentsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (isBlank(nextPageLink)) {
      return Mono.empty();
    }

    return executePagedRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                                   .listBlueprintAssignmentsNextPage(getAzureBearerAuthToken(azureConfig), nextPageLink,
                                       AzureBlueprintRestClient.APP_VERSION),
        AssignmentList.class);
  }

  @Override
  public PagedFlux<AssignmentOperation> listAssignmentOperations(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    return new PagedFlux(()
                             -> listAssignmentOperationsSinglePageAsync(azureConfig, resourceScope, assignmentName),
        nextLink -> listAssignmentOperationsNextSinglePageAsync(azureConfig, String.valueOf(nextLink)));
  }

  public Mono<PagedResponse<AssignmentOperation>> listAssignmentOperationsSinglePageAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    if (isBlank(resourceScope)) {
      Mono.error(new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG));
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      Mono.error(new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope)));
    }
    if (isBlank(assignmentName)) {
      Mono.error(new IllegalArgumentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG));
    }

    return executePagedRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                                   .listAssignmentOperations(getAzureBearerAuthToken(azureConfig), resourceScope,
                                       assignmentName, AzureBlueprintRestClient.APP_VERSION),
        AssignmentOperationList.class);
  }

  public Mono<PagedResponse<AssignmentOperation>> listAssignmentOperationsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (isBlank(nextPageLink)) {
      return Mono.empty();
    }

    return executePagedRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                                   .listAssignmentOperationsNextPage(getAzureBearerAuthToken(azureConfig), nextPageLink,
                                       AzureBlueprintRestClient.APP_VERSION),
        AssignmentOperationList.class);
  }

  @Override
  public WhoIsBlueprintContract whoIsBlueprint(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    return whoIsBlueprintWithServiceResponseAsync(azureConfig, resourceScope, assignmentName).block();
  }

  private Mono<WhoIsBlueprintContract> whoIsBlueprintWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    if (isBlank(resourceScope)) {
      throw new AzureBPDeploymentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new AzureBPDeploymentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new AzureBPDeploymentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }

    return getMonoRequest(getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
                              .whoIsBlueprint(getAzureBearerAuthToken(azureConfig), resourceScope, assignmentName,
                                  AzureBlueprintRestClient.APP_VERSION),
        WhoIsBlueprintContract.class);
  }
}
