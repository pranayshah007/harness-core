/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import static io.harness.pms.plan.creation.PlanCreatorUtils.supportsField;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.contracts.plan.VariablesCreationBlobRequest;
import io.harness.pms.contracts.plan.VariablesCreationBlobResponse;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.plan.creation.PlanCreationBlobResponseUtils;
import io.harness.pms.sdk.core.pipeline.creators.BaseCreatorService;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoDecorator;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class VariableCreatorService
    extends BaseCreatorService<VariableCreationResponse, SetupMetadata, VariablesCreationBlobRequest> {
  private final PipelineServiceInfoDecorator serviceInfoDecorator;
  private final PmsGitSyncHelper pmsGitSyncHelper;

  @Inject
  public VariableCreatorService(PipelineServiceInfoDecorator serviceInfoDecorator, PmsGitSyncHelper pmsGitSyncHelper) {
    this.serviceInfoDecorator = serviceInfoDecorator;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
  }

  public VariablesCreationBlobResponse createVariablesResponse(VariablesCreationBlobRequest request) {
    Dependencies initialDependencies = request.getDeps();

    try (PmsGitSyncBranchContextGuard ignore = pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(
             request.getMetadata().getGitSyncBranchContext(), true)) {
      VariableCreationResponse response = processNodesRecursively(
          initialDependencies, SetupMetadata.newBuilder().build(), VariableCreationResponse.builder().build(), request);
      return response.toBlobResponse();
    }
  }

  public VariableCreationResponse processNodeInternal(
      SetupMetadata setupMetadata, YamlField yamlField, VariablesCreationBlobRequest request) {
    Optional<VariableCreator> variableCreatorOptional =
        findVariableCreator(serviceInfoDecorator.getVariableCreators(), yamlField);

    if (!variableCreatorOptional.isPresent()) {
      return null;
    }

    VariableCreationResponse response;
    VariableCreator variableCreator = variableCreatorOptional.get();

    if (request.getMetadata().getMetadataMap().containsKey("newVersion")) {
      try {
        Class<?> cls = variableCreator.getFieldClass();
        if (cls == null) {
          return VariableCreationResponse.builder().build();
        }
        Object obj =
            YamlField.class.isAssignableFrom(cls) ? yamlField : YamlUtils.read(yamlField.getNode().toString(), cls);
        VariableCreationContext variableCreationContext =
            VariableCreationContext.builder().currentField(yamlField).build();
        addAccountOrgProjectIdentifiers(request, variableCreationContext);
        response = variableCreator.createVariablesForFieldV2(variableCreationContext, obj);
      } catch (IOException ex) {
        String message = format("Invalid yaml path [%s] during execution variable creation", yamlField.getYamlPath());
        log.error(message, ex);
        throw new InvalidRequestException(message, ex);
      } catch (Throwable t) {
        String message = format("Error for [%s] during execution variable creation", yamlField.getYamlPath());
        log.error(message, t);
        throw new InvalidRequestException(message);
      }
    } else {
      response = variableCreator.createVariablesForField(
          VariableCreationContext.builder().currentField(yamlField).build(), yamlField);
    }
    return response;
  }

  private void addAccountOrgProjectIdentifiers(VariablesCreationBlobRequest request, VariableCreationContext context) {
    context.put(
        NGCommonEntityConstants.ORG_KEY, request.getMetadata().getMetadataMap().get(NGCommonEntityConstants.ORG_KEY));
    context.put(NGCommonEntityConstants.PROJECT_KEY,
        request.getMetadata().getMetadataMap().get(NGCommonEntityConstants.PROJECT_KEY));
    context.put(NGCommonEntityConstants.ACCOUNT_KEY,
        request.getMetadata().getMetadataMap().get(NGCommonEntityConstants.ACCOUNT_KEY));
  }

  @Override
  public void mergeResponses(
      VariableCreationResponse finalResponse, VariableCreationResponse response, Dependencies.Builder dependencies) {
    finalResponse.mergeResponses(response);
    if (response.getYamlUpdates() != null && EmptyPredicate.isNotEmpty(response.getYamlUpdates().getFqnToYamlMap())) {
      String updatedYaml = PlanCreationBlobResponseUtils.mergeYamlUpdates(
          dependencies.getYaml(), finalResponse.getYamlUpdates().getFqnToYamlMap());
      finalResponse.updateYamlInDependencies(updatedYaml);
      dependencies.setYaml(updatedYaml);
    }
  }

  private Optional<VariableCreator> findVariableCreator(List<VariableCreator> variableCreators, YamlField yamlField) {
    if (EmptyPredicate.isEmpty(variableCreators)) {
      return Optional.empty();
    }
    return variableCreators.stream()
        .filter(variableCreator -> {
          Map<String, Set<String>> supportedTypes = variableCreator.getSupportedTypes();
          return supportsField(supportedTypes, yamlField, PipelineVersion.V0);
        })
        .findFirst();
  }
}
