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
import static io.harness.azure.model.AzureConstants.NEXT_PAGE_LINK_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VERSION_ID_BLANK_VALIDATION_MSG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.client.AzureBlueprintRestClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.blueprint.Blueprint;
import io.harness.azure.model.blueprint.PublishedBlueprint;
import io.harness.azure.model.blueprint.artifact.Artifact;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.WhoIsBlueprintContract;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentOperation;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.reflect.TypeToken;
import com.google.inject.Singleton;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.resources.implementation.PageImpl;
import com.microsoft.rest.ServiceResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Response;
import rx.Observable;
import rx.functions.Func1;

@Singleton
@Slf4j
public class AzureBlueprintClientImpl extends AzureClient implements AzureBlueprintClient {
  @Override
  public Blueprint createOrUpdateBlueprint(final AzureConfig azureConfig, final String resourceScope,
      final String blueprintName, final String blueprintJSON) {
    return createOrUpdateBlueprintWithServiceResponseAsync(azureConfig, resourceScope, blueprintName, blueprintJSON)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<Blueprint>> createOrUpdateBlueprintWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName,
      final String blueprintJSON) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintJSON)) {
      throw new IllegalArgumentException(BLUEPRINT_JSON_BLANK_VALIDATION_MSG);
    }
    if (isBlank(blueprintName)) {
      throw new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }

    JsonNode blueprintObj = JsonUtils.readTree(blueprintJSON);

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .createOrUpdateBlueprint(getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName, blueprintObj,
            AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Blueprint>>>) response -> {
          try {
            ServiceResponse<Blueprint> clientResponse = createOrUpdateBlueprintDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<Blueprint> createOrUpdateBlueprintDelegate(Response<ResponseBody> response)
      throws IOException {
    return serviceResponseFactory.<Blueprint, CloudException>newInstance(azureJacksonAdapter)
        .register(201, (new TypeToken<Blueprint>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public Blueprint getBlueprint(final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    return getBlueprintWithServiceResponseAsync(azureConfig, resourceScope, blueprintName).toBlocking().single().body();
  }

  private Observable<ServiceResponse<Blueprint>> getBlueprintWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .getBlueprint(
            getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Blueprint>>>) response -> {
          try {
            ServiceResponse<Blueprint> clientResponse = getBlueprintDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<Blueprint> getBlueprintDelegate(Response<ResponseBody> response) throws IOException {
    return serviceResponseFactory.<Blueprint, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<Blueprint>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public Artifact createOrUpdateArtifact(
      AzureConfig azureConfig, String resourceScope, String blueprintName, String artifactName, String artifactJSON) {
    return createOrUpdateArtifactWithServiceResponseAsync(
        azureConfig, resourceScope, blueprintName, artifactName, artifactJSON)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<Artifact>> createOrUpdateArtifactWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String artifactName,
      final String artifactJSON) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(artifactJSON)) {
      throw new IllegalArgumentException(ARTIFACT_JSON_BLANK_VALIDATION_MSG);
    }
    if (isBlank(artifactName)) {
      throw new IllegalArgumentException(ARTIFACT_NAME_BLANK_VALIDATION_MSG);
    }

    JsonNode artifactObj = JsonUtils.readTree(artifactJSON);

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .createOrUpdateArtifact(getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName, artifactName,
            artifactObj, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Artifact>>>) response -> {
          try {
            ServiceResponse<Artifact> clientResponse = createOrUpdateArtifactDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<Artifact> createOrUpdateArtifactDelegate(Response<ResponseBody> response) throws IOException {
    return serviceResponseFactory.<Artifact, CloudException>newInstance(azureJacksonAdapter)
        .register(201, (new TypeToken<Artifact>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public PublishedBlueprint publishBlueprintDefinition(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    return publishBlueprintDefinitionWithServiceResponseAsync(azureConfig, resourceScope, blueprintName, versionId)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<PublishedBlueprint>> publishBlueprintDefinitionWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(versionId)) {
      throw new IllegalArgumentException(VERSION_ID_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .publishBlueprintDefinition(getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName, versionId,
            AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<PublishedBlueprint>>>) response -> {
          try {
            ServiceResponse<PublishedBlueprint> clientResponse = publishBlueprintDefinitionDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<PublishedBlueprint> publishBlueprintDefinitionDelegate(Response<ResponseBody> response)
      throws IOException {
    return serviceResponseFactory.<PublishedBlueprint, CloudException>newInstance(azureJacksonAdapter)
        .register(201, (new TypeToken<PublishedBlueprint>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public PublishedBlueprint getPublishedBlueprintVersion(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    return getPublishedBlueprintVersionWithServiceResponseAsync(azureConfig, resourceScope, blueprintName, versionId)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<PublishedBlueprint>> getPublishedBlueprintVersionWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName, final String versionId) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(versionId)) {
      throw new IllegalArgumentException(VERSION_ID_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .getPublishedBlueprintVersion(getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName, versionId,
            AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<PublishedBlueprint>>>) response -> {
          try {
            ServiceResponse<PublishedBlueprint> clientResponse = getPublishedBlueprintVersionDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<PublishedBlueprint> getPublishedBlueprintVersionDelegate(Response<ResponseBody> response)
      throws IOException {
    return serviceResponseFactory.<PublishedBlueprint, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<PublishedBlueprint>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public PagedList<PublishedBlueprint> listPublishedBlueprintVersions(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    ServiceResponse<Page<PublishedBlueprint>> response =
        listPublishedBlueprintVersionsSinglePageAsync(azureConfig, resourceScope, blueprintName).toBlocking().single();

    return new PagedList<PublishedBlueprint>(response.body()) {
      @Override
      public Page<PublishedBlueprint> nextPage(String nextPageLink) {
        return listPublishedBlueprintVersionsNextSinglePageAsync(azureConfig, nextPageLink)
            .toBlocking()
            .single()
            .body();
      }
    };
  }

  public Observable<ServiceResponse<Page<PublishedBlueprint>>> listPublishedBlueprintVersionsSinglePageAsync(
      final AzureConfig azureConfig, final String resourceScope, final String blueprintName) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(blueprintName)) {
      throw new IllegalArgumentException(BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .listPublishedBlueprintVersions(
            getAzureBearerAuthToken(azureConfig), resourceScope, blueprintName, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Page<PublishedBlueprint>>>>) response -> {
          try {
            ServiceResponse<PageImpl<PublishedBlueprint>> result = listPublishedBlueprintVersionsDelegate(response);
            return Observable.just(new ServiceResponse<Page<PublishedBlueprint>>(result.body(), result.response()));
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  public Observable<ServiceResponse<Page<PublishedBlueprint>>> listPublishedBlueprintVersionsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (nextPageLink == null) {
      throw new IllegalArgumentException(NEXT_PAGE_LINK_BLANK_VALIDATION_MSG);
    }
    String nextUrl = String.format("%s", nextPageLink);

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .listNext(getAzureBearerAuthToken(azureConfig), nextUrl, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Page<PublishedBlueprint>>>>) response -> {
          try {
            ServiceResponse<PageImpl<PublishedBlueprint>> result = listPublishedBlueprintVersionsDelegate(response);
            return Observable.just(new ServiceResponse<Page<PublishedBlueprint>>(result.body(), result.response()));
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<PageImpl<PublishedBlueprint>> listPublishedBlueprintVersionsDelegate(
      Response<ResponseBody> response) throws IOException {
    return serviceResponseFactory.<PageImpl<PublishedBlueprint>, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<PageImpl<PublishedBlueprint>>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public Assignment beginCreateOrUpdateAssignment(final AzureConfig azureConfig, final String resourceScope,
      final String assignmentName, final String assignmentJSON) {
    return beginCreateOrUpdateAssignmentWithServiceResponseAsync(
        azureConfig, resourceScope, assignmentName, assignmentJSON)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<Assignment>> beginCreateOrUpdateAssignmentWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName,
      final String assignmentJSON) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new IllegalArgumentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(assignmentJSON)) {
      throw new IllegalArgumentException(ASSIGNMENT_JSON_BLANK_VALIDATION_MSG);
    }

    JsonNode assignmentObj = JsonUtils.readTree(assignmentJSON);

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .beginCreateOrUpdateAssignment(getAzureBearerAuthToken(azureConfig), resourceScope, assignmentName,
            assignmentObj, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Assignment>>>) response -> {
          try {
            ServiceResponse<Assignment> clientResponse = beginCreateOrUpdateAssignmentDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<Assignment> beginCreateOrUpdateAssignmentDelegate(Response<ResponseBody> response)
      throws IOException {
    return serviceResponseFactory.<Assignment, CloudException>newInstance(azureJacksonAdapter)
        .register(201, (new TypeToken<Assignment>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public Assignment getAssignment(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    return getAssignmentWithServiceResponseAsync(azureConfig, resourceScope, assignmentName)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<Assignment>> getAssignmentWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new IllegalArgumentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .getAssignment(
            getAzureBearerAuthToken(azureConfig), resourceScope, assignmentName, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Assignment>>>) response -> {
          try {
            ServiceResponse<Assignment> clientResponse = getAssignmentDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<Assignment> getAssignmentDelegate(Response<ResponseBody> response) throws IOException {
    return serviceResponseFactory.<Assignment, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<Assignment>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public PagedList<Assignment> listAssignments(final AzureConfig azureConfig, final String resourceScope) {
    ServiceResponse<Page<Assignment>> response =
        listBlueprintAssignmentsSinglePageAsync(azureConfig, resourceScope).toBlocking().single();

    return new PagedList<Assignment>(response.body()) {
      @Override
      public Page<Assignment> nextPage(String nextPageLink) {
        return listBlueprintAssignmentsNextSinglePageAsync(azureConfig, nextPageLink).toBlocking().single().body();
      }
    };
  }

  public Observable<ServiceResponse<Page<Assignment>>> listBlueprintAssignmentsSinglePageAsync(
      final AzureConfig azureConfig, final String resourceScope) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .listBlueprintAssignments(
            getAzureBearerAuthToken(azureConfig), resourceScope, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Page<Assignment>>>>) response -> {
          try {
            ServiceResponse<PageImpl<Assignment>> result = listBlueprintAssignmentsDelegate(response);
            return Observable.just(new ServiceResponse<Page<Assignment>>(result.body(), result.response()));
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  public Observable<ServiceResponse<Page<Assignment>>> listBlueprintAssignmentsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (nextPageLink == null) {
      throw new IllegalArgumentException(NEXT_PAGE_LINK_BLANK_VALIDATION_MSG);
    }
    String nextUrl = String.format("%s", nextPageLink);

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .listNext(getAzureBearerAuthToken(azureConfig), nextUrl, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Page<Assignment>>>>) response -> {
          try {
            ServiceResponse<PageImpl<Assignment>> result = listBlueprintAssignmentsDelegate(response);
            return Observable.just(new ServiceResponse<Page<Assignment>>(result.body(), result.response()));
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<PageImpl<Assignment>> listBlueprintAssignmentsDelegate(Response<ResponseBody> response)
      throws IOException {
    return serviceResponseFactory.<PageImpl<Assignment>, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<PageImpl<Assignment>>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public PagedList<AssignmentOperation> listAssignmentOperations(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    ServiceResponse<Page<AssignmentOperation>> response =
        listAssignmentOperationsSinglePageAsync(azureConfig, resourceScope, assignmentName).toBlocking().single();

    return new PagedList<AssignmentOperation>(response.body()) {
      @Override
      public Page<AssignmentOperation> nextPage(String nextPageLink) {
        return listAssignmentOperationsNextSinglePageAsync(azureConfig, nextPageLink).toBlocking().single().body();
      }
    };
  }

  public Observable<ServiceResponse<Page<AssignmentOperation>>> listAssignmentOperationsSinglePageAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new IllegalArgumentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .listAssignmentOperations(
            getAzureBearerAuthToken(azureConfig), resourceScope, assignmentName, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Page<AssignmentOperation>>>>) response -> {
          try {
            ServiceResponse<PageImpl<AssignmentOperation>> result = listAssignmentOperationsDelegate(response);
            return Observable.just(new ServiceResponse<Page<AssignmentOperation>>(result.body(), result.response()));
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  public Observable<ServiceResponse<Page<AssignmentOperation>>> listAssignmentOperationsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (nextPageLink == null) {
      throw new IllegalArgumentException(NEXT_PAGE_LINK_BLANK_VALIDATION_MSG);
    }
    String nextUrl = String.format("%s", nextPageLink);

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .listNext(getAzureBearerAuthToken(azureConfig), nextUrl, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<Page<AssignmentOperation>>>>) response -> {
          try {
            ServiceResponse<PageImpl<AssignmentOperation>> result = listAssignmentOperationsDelegate(response);
            return Observable.just(new ServiceResponse<Page<AssignmentOperation>>(result.body(), result.response()));
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<PageImpl<AssignmentOperation>> listAssignmentOperationsDelegate(
      Response<ResponseBody> response) throws IOException {
    return serviceResponseFactory.<PageImpl<AssignmentOperation>, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<PageImpl<AssignmentOperation>>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }

  @Override
  public WhoIsBlueprintContract whoIsBlueprint(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    return whoIsBlueprintWithServiceResponseAsync(azureConfig, resourceScope, assignmentName)
        .toBlocking()
        .single()
        .body();
  }

  private Observable<ServiceResponse<WhoIsBlueprintContract>> whoIsBlueprintWithServiceResponseAsync(
      final AzureConfig azureConfig, final String resourceScope, final String assignmentName) {
    if (isBlank(resourceScope)) {
      throw new IllegalArgumentException(RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (!AzureResourceUtility.isValidResourceScope(resourceScope)) {
      throw new IllegalArgumentException(format(RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG, resourceScope));
    }
    if (isBlank(assignmentName)) {
      throw new IllegalArgumentException(ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }

    return getAzureBlueprintRestClient(azureConfig.getAzureEnvironmentType())
        .whoIsBlueprint(
            getAzureBearerAuthToken(azureConfig), resourceScope, assignmentName, AzureBlueprintRestClient.APP_VERSION)
        .flatMap((Func1<Response<ResponseBody>, Observable<ServiceResponse<WhoIsBlueprintContract>>>) response -> {
          try {
            ServiceResponse<WhoIsBlueprintContract> clientResponse = whoIsBlueprintDelegate(response);
            return Observable.just(clientResponse);
          } catch (Exception t) {
            return Observable.error(ExceptionMessageSanitizer.sanitizeException(t));
          }
        });
  }

  private ServiceResponse<WhoIsBlueprintContract> whoIsBlueprintDelegate(Response<ResponseBody> response)
      throws IOException {
    return serviceResponseFactory.<WhoIsBlueprintContract, CloudException>newInstance(azureJacksonAdapter)
        .register(200, (new TypeToken<WhoIsBlueprintContract>() {}).getType())
        .registerError(CloudException.class)
        .build(response);
  }
}
