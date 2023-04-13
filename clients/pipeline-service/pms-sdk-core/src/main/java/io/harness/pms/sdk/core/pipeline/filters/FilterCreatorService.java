/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.filters;

import static io.harness.pms.plan.creation.PlanCreatorUtils.supportsField;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.FilterCreationBlobRequest;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.creators.BaseCreatorService;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoDecorator;
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
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class FilterCreatorService
    extends BaseCreatorService<FilterCreationResponse, SetupMetadata, FilterCreationBlobRequest> {
  private final PipelineServiceInfoDecorator serviceInfoDecorator;
  private final FilterCreationResponseMerger filterCreationResponseMerger;
  private final PmsGitSyncHelper pmsGitSyncHelper;

  @Inject
  public FilterCreatorService(@NotNull PipelineServiceInfoDecorator serviceInfoDecorator,
      @NotNull FilterCreationResponseMerger filterCreationResponseMerger, PmsGitSyncHelper pmsGitSyncHelper) {
    this.serviceInfoDecorator = serviceInfoDecorator;
    this.filterCreationResponseMerger = filterCreationResponseMerger;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
  }

  public FilterCreationBlobResponse createFilterBlobResponse(FilterCreationBlobRequest request) {
    Dependencies initialDependencies = request.getDeps();

    SetupMetadata setupMetadata = request.getSetupMetadata();
    try (PmsGitSyncBranchContextGuard ignore =
             pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(setupMetadata.getGitSyncBranchContext(), true)) {
      FilterCreationResponse finalResponse = processNodesRecursively(
          initialDependencies, setupMetadata, FilterCreationResponse.builder().build(), request);
      return finalResponse.toBlobResponse();
    }
  }

  private Optional<FilterJsonCreator> findFilterCreator(
      List<FilterJsonCreator> filterJsonCreators, YamlField yamlField) {
    return filterJsonCreators.stream()
        .filter(filterJsonCreator -> {
          Map<String, Set<String>> supportedTypes = filterJsonCreator.getSupportedTypes();
          return supportsField(supportedTypes, yamlField, PipelineVersion.V0);
        })
        .findFirst();
  }

  @Override
  public FilterCreationResponse processNodeInternal(
      SetupMetadata setupMetadata, YamlField yamlField, FilterCreationBlobRequest request) {
    Optional<FilterJsonCreator> filterCreatorOptional =
        findFilterCreator(serviceInfoDecorator.getFilterJsonCreators(), yamlField);

    if (!filterCreatorOptional.isPresent()) {
      return null;
    }

    FilterCreationResponse response;
    FilterJsonCreator filterJsonCreator = filterCreatorOptional.get();
    Class<?> clazz = filterJsonCreator.getFieldClass();
    if (YamlField.class.isAssignableFrom(clazz)) {
      response = filterJsonCreator.handleNode(
          FilterCreationContext.builder().currentField(yamlField).setupMetadata(setupMetadata).build(), yamlField);
    } else {
      try {
        Object obj = YamlUtils.read(yamlField.getNode().toString(), clazz);
        response = filterJsonCreator.handleNode(
            FilterCreationContext.builder().currentField(yamlField).setupMetadata(setupMetadata).build(), obj);
      } catch (IOException e) {
        // YamlUtils.getErrorNodePartialFQN() uses exception path to build FQN
        if (e.getCause() instanceof InvalidYamlException) {
          log.error(e.getMessage());
          throw new InvalidYamlException(e.getCause().getMessage());
        }
        log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(yamlField.getNode(), e)), e);
        throw new InvalidYamlRuntimeException("Invalid yaml in node [%s]", e, yamlField.getNode());
      }
    }
    return response;
  }

  @Override
  public void mergeResponses(
      FilterCreationResponse finalResponse, FilterCreationResponse response, Dependencies.Builder dependencies) {
    finalResponse.setStageCount(finalResponse.getStageCount() + response.getStageCount());
    finalResponse.addReferredEntities(response.getReferredEntities());
    finalResponse.addStageNames(response.getStageNames());
    filterCreationResponseMerger.mergeFilterCreationResponse(finalResponse, response);
  }
}
