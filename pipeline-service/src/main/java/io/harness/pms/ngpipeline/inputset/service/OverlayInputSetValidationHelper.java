/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.OverlayInputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class OverlayInputSetValidationHelper {
  public void validateOverlayInputSet(PMSInputSetService inputSetService, InputSetEntity inputSetEntity) {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();

    String identifier = InputSetYamlHelper.getStringField(yaml, "identifier", "overlayInputSet");
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("Identifier cannot be empty");
    }
    if (identifier.length() > 63) {
      throw new InvalidRequestException("Overlay Input Set identifier length cannot be more that 63 characters.");
    }
    List<String> inputSetReferences = InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml);
    if (inputSetReferences.isEmpty()) {
      throw new InvalidRequestException("Input Set References can't be empty");
    }

    InputSetYamlHelper.confirmPipelineIdentifierInOverlayInputSet(yaml, pipelineIdentifier);
    InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml, "overlayInputSet", orgIdentifier, projectIdentifier);

    List<Optional<InputSetEntity>> inputSets;
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      String repoIdentifier = GitContextHelper.getGitEntityInfo().getYamlGitConfigId();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder()
              .gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).yamlGitConfigId(repoIdentifier).build())
              .build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        inputSets = findAllReferredInputSets(
            inputSetService, inputSetReferences, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      }
    } else {
      inputSets = findAllReferredInputSets(
          inputSetService, inputSetReferences, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    Map<String, String> invalidReferences =
        InputSetErrorsHelper.getInvalidInputSetReferences(inputSets, inputSetReferences);
    if (!invalidReferences.isEmpty()) {
      OverlayInputSetErrorWrapperDTOPMS overlayInputSetErrorWrapperDTOPMS =
          OverlayInputSetErrorWrapperDTOPMS.builder().invalidReferences(invalidReferences).build();
      throw new InvalidOverlayInputSetException(
          "Some fields in the Overlay Input Set are invalid.", overlayInputSetErrorWrapperDTOPMS, inputSetEntity);
    }
  }

  private List<Optional<InputSetEntity>> findAllReferredInputSets(PMSInputSetService inputSetService,
      List<String> referencesInOverlay, String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier) {
    List<Optional<InputSetEntity>> inputSets = new ArrayList<>();
    referencesInOverlay.forEach(identifier -> {
      if (EmptyPredicate.isEmpty(identifier)) {
        throw new InvalidRequestException("Empty Input Set Identifier not allowed in Input Set References");
      }
      inputSets.add(inputSetService.getWithoutValidations(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, false));
    });
    return inputSets;
  }

  public static InputSetYamlDiffDTO getYAMLDiffForOverlayInputSet(
      PMSInputSetService inputSetService, InputSetEntity inputSetEntity) {
    // todo: implement this, and change overlay input set validation in validateOverlayInputSet accordingly
    /*
    1. get references in ois
    2. create criteria for db call
    3. input set list call
    4. remove non-existing references from the references in ois
    5.1 if some references remain, return
    5.2 if not, check if normal input sets exist for the pipeline.
    5.2.1 if no references remain, don't allow start afresh
    5.2.2 if remaining, allow start afresh
     */
    return null;
  }
}
