/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.variables;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.VariablesCreationBlobRequest;
import io.harness.pms.contracts.plan.VariablesCreationBlobResponse;
import io.harness.pms.contracts.plan.VariablesCreationMetadata;
import io.harness.pms.contracts.plan.VariablesCreationResponse;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.variable.remote.VariableClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class VariableCreatorMergeService {
  private final PmsSdkHelper pmsSdkHelper;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private Map<String, List<String>> serviceExpressionMap;
  @Inject private OrganizationClient organizationClient;
  @Inject private ProjectClient projectClient;
  @Inject private VariableClient variableClient;

  private static final int MAX_DEPTH = 10;
  private final Executor executor = Executors.newFixedThreadPool(5);

  @Inject
  public VariableCreatorMergeService(PmsSdkHelper pmsSdkHelper, PmsGitSyncHelper pmsGitSyncHelper) {
    this.pmsSdkHelper = pmsSdkHelper;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
  }

  public VariableMergeServiceResponse createVariablesResponses(String yaml, boolean newVersion) {
    try {
      return createVariablesResponse(yaml, newVersion);
    } catch (Exception ex) {
      log.error("Error happened while creating variables for pipeline:", ex);
      throw new InvalidRequestException(
          format("Error happened while creating variables for pipeline: %s", ex.getMessage()));
    }
  }

  public VariableMergeServiceResponse createVariablesResponsesV2(
      String accountId, String orgId, String projectId, String yaml) {
    try {
      return createVariablesResponseV2(accountId, orgId, projectId, yaml);
    } catch (Exception ex) {
      log.error("Error happened while creating variables for pipeline:", ex);
      throw new InvalidRequestException(
          format("Error happened while creating variables for pipeline: %s", ex.getMessage()));
    }
  }

  public VariableMergeServiceResponse createVariablesResponse(@NotNull String yaml, boolean newVersion)
      throws IOException {
    Map<String, PlanCreatorServiceInfo> services = pmsSdkHelper.getServices();

    YamlField processedYaml;
    if (!newVersion) {
      processedYaml = YamlUtils.injectUuidWithLeafUuid(yaml);
    } else {
      processedYaml = YamlUtils.injectUuidInYamlField(yaml);
    }
    YamlField topRootFieldInYaml =
        YamlUtils.getTopRootFieldInYamlField(Objects.requireNonNull(processedYaml).getNode());

    Dependencies dependencies =
        Dependencies.newBuilder()
            .setYaml(processedYaml.getNode().getCurrJsonNode().toString())
            .putDependencies(topRootFieldInYaml.getNode().getUuid(), topRootFieldInYaml.getNode().getYamlPath())
            .build();
    VariablesCreationMetadata.Builder metadataBuilder = VariablesCreationMetadata.newBuilder();
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
    if (gitSyncBranchContext != null) {
      metadataBuilder.setGitSyncBranchContext(gitSyncBranchContext);
    }
    // TODO(archit): delete variables v1 api and this newVersion flag
    if (newVersion) {
      metadataBuilder.putMetadata("newVersion", "newVersion");
    }

    VariablesCreationBlobResponse response =
        createVariablesForDependenciesRecursive(services, dependencies, metadataBuilder.build());

    // for backward compatible v1 api changes
    String responseYaml = newVersion ? response.getDeps().getYaml() : YamlUtils.writeYamlString(processedYaml);

    return VariableCreationBlobResponseUtils.getMergeServiceResponse(
        responseYaml, response, serviceExpressionMap, newVersion);
  }

  public VariableMergeServiceResponse createVariablesResponseV2(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String yaml) throws IOException {
    Map<String, PlanCreatorServiceInfo> services = pmsSdkHelper.getServices();

    YamlField processedYaml = YamlUtils.injectUuidInYamlField(yaml);

    YamlField topRootFieldInYaml =
        YamlUtils.getTopRootFieldInYamlField(Objects.requireNonNull(processedYaml).getNode());

    Dependencies dependencies =
        Dependencies.newBuilder()
            .setYaml(processedYaml.getNode().getCurrJsonNode().toString())
            .putDependencies(topRootFieldInYaml.getNode().getUuid(), topRootFieldInYaml.getNode().getYamlPath())
            .build();
    VariablesCreationMetadata.Builder metadataBuilder = VariablesCreationMetadata.newBuilder();
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
    if (gitSyncBranchContext != null) {
      metadataBuilder.setGitSyncBranchContext(gitSyncBranchContext);
    }
    // TODO(archit): delete variables v1 api and this newVersion flag
    metadataBuilder.putMetadata("newVersion", "newVersion");

    metadataBuilder.putMetadata(NGCommonEntityConstants.ACCOUNT_KEY, accountId);
    metadataBuilder.putMetadata(NGCommonEntityConstants.ORG_KEY, orgIdentifier);
    metadataBuilder.putMetadata(NGCommonEntityConstants.PROJECT_KEY, projectIdentifier);
    VariablesCreationBlobResponse response =
        createVariablesForDependenciesRecursive(services, dependencies, metadataBuilder.build());

    // for backward compatible v1 api changes
    String responseYaml = response.getDeps().getYaml();

    // Add account, org and project expressions

    return VariableCreationBlobResponseUtils.getMergeServiceResponse(
        responseYaml, response, getPipelineMetadataExpressions(accountId, orgIdentifier, projectIdentifier), true);
  }

  private VariablesCreationBlobResponse createVariablesForDependenciesRecursive(
      Map<String, PlanCreatorServiceInfo> services, Dependencies initialDependencies,
      VariablesCreationMetadata metadata) {
    VariablesCreationBlobResponse.Builder finalResponseBuilder =
        VariablesCreationBlobResponse.newBuilder().setDeps(initialDependencies);
    if (isEmpty(services) || isEmpty(initialDependencies.getDependenciesMap())) {
      return finalResponseBuilder.build();
    }

    // This map is for storing those dependencies which cannot be resolved by anyone.
    // We don't want to return early, thus we are storing unresolved dependencies so that variable resolution keeps on
    // working for other entities.
    Dependencies.Builder unresolvedDependenciesMap = Dependencies.newBuilder();
    for (int i = 0; i < MAX_DEPTH && isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap()); i++) {
      VariablesCreationBlobResponse variablesCreationBlobResponse =
          obtainVariablesPerIteration(services, finalResponseBuilder, metadata);
      VariableCreationBlobResponseUtils.mergeResolvedDependencies(finalResponseBuilder, variablesCreationBlobResponse);
      unresolvedDependenciesMap.putAllDependencies(finalResponseBuilder.getDeps().getDependenciesMap());
      finalResponseBuilder.setDeps(finalResponseBuilder.getDeps().toBuilder().clearDependencies().build());

      VariableCreationBlobResponseUtils.mergeDependencies(finalResponseBuilder, variablesCreationBlobResponse);
      VariableCreationBlobResponseUtils.mergeProperties(
          finalResponseBuilder, variablesCreationBlobResponse, metadata.getMetadataMap().containsKey("newVersion"));
    }

    finalResponseBuilder.setDeps(
        finalResponseBuilder.getDeps().toBuilder().putAllDependencies(unresolvedDependenciesMap.getDependenciesMap()));
    return finalResponseBuilder.build();
  }

  private VariablesCreationBlobResponse obtainVariablesPerIteration(Map<String, PlanCreatorServiceInfo> services,
      VariablesCreationBlobResponse.Builder responseBuilder, VariablesCreationMetadata metadata) {
    CompletableFutures<VariablesCreationResponse> completableFutures = new CompletableFutures<>(executor);
    for (Map.Entry<String, PlanCreatorServiceInfo> entry : services.entrySet()) {
      if (!PmsSdkHelper.containsSupportedDependencyByYamlPath(entry.getValue(), responseBuilder.getDeps())) {
        continue;
      }

      completableFutures.supplyAsync(() -> {
        try {
          return entry.getValue().getPlanCreationClient().createVariablesYaml(VariablesCreationBlobRequest.newBuilder()
                                                                                  .setDeps(responseBuilder.getDeps())
                                                                                  .setMetadata(metadata)
                                                                                  .build());
        } catch (Exception ex) {
          log.error(String.format("Error connecting with service: [%s]. Is this service Running?", entry.getKey()), ex);
          ErrorResponse errorResponse = ErrorResponse.newBuilder()
                                            .addMessages(format("Error connecting with service: [%s]", entry.getKey()))
                                            .build();
          VariablesCreationBlobResponse blobResponse =
              VariablesCreationBlobResponse.newBuilder().addErrorResponse(errorResponse).build();
          return VariablesCreationResponse.newBuilder().setBlobResponse(blobResponse).build();
        }
      });
    }

    try {
      VariablesCreationBlobResponse.Builder builder = VariablesCreationBlobResponse.newBuilder();
      List<VariablesCreationResponse> variablesCreationResponses = completableFutures.allOf().get(5, TimeUnit.MINUTES);
      variablesCreationResponses.forEach(response -> {
        VariableCreationBlobResponseUtils.mergeResponses(
            builder, response.getBlobResponse(), metadata.getMetadataMap().containsKey("newVersion"));
        if (response.getResponseCase() == VariablesCreationResponse.ResponseCase.ERRORRESPONSE) {
          builder.addErrorResponse(response.getErrorResponse());
        }
      });
      return builder.build();
    } catch (Exception ex) {
      throw new UnexpectedException("Error fetching variables creation response from service", ex);
    }
  }

  private Map<String, List<String>> getPipelineMetadataExpressions(
      @NotNull String accountId, @NotNull String orgIdentifier, @NotNull String projectIdentifier) {
    Map<String, List<String>> resultMap = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : serviceExpressionMap.entrySet()) {
      resultMap.put(entry.getKey(), entry.getValue());
    }

    // Adding account expressions
    resultMap.put("account", VariableCreatorHelper.getExpressionsInObject(AccountDTO.builder().build(), "account"));
    // Adding org expressions
    try {
      Optional<OrganizationResponse> resp =
          SafeHttpCall.execute(organizationClient.getOrganization(orgIdentifier, accountId)).getData();
      OrganizationDTO organizationDTO = resp.map(OrganizationResponse::getOrganization).orElse(null);
      resultMap.put("org", VariableCreatorHelper.getExpressionsInObject(organizationDTO, "org"));
    } catch (Exception ex) {
      log.error("Couldn't get organisation details", ex);
    }
    // Adding project details
    try {
      Optional<ProjectResponse> resp =
          SafeHttpCall.execute(projectClient.getProject(projectIdentifier, accountId, orgIdentifier)).getData();
      ProjectDTO projectDTO = resp.map(ProjectResponse::getProject).orElse(null);
      resultMap.put("project", VariableCreatorHelper.getExpressionsInObject(projectDTO, "project"));
    } catch (Exception ex) {
      log.error("Couldn't get project details", ex);
    }
    // Adding variable details
    try {
      resultMap.put("variable",
          SafeHttpCall.execute(variableClient.getExpressions(accountId, orgIdentifier, projectIdentifier)).getData());
    } catch (Exception ex) {
      log.error("Couldn't get variable details", ex);
    }
    return resultMap;
  }
}
