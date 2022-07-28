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
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.OverlayInputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;

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

    List<Optional<InputSetEntity>> inputSets = findAllReferredInputSets(
        inputSetService, inputSetReferences, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
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
    if (GitContextHelper.isUpdateToNewBranch()) {
      String baseBranch = Objects.requireNonNull(GitContextHelper.getGitEntityInfo()).getBaseBranch();
      String repoIdentifier = GitContextHelper.getGitEntityInfo().getYamlGitConfigId();
      GitSyncBranchContext branchContext =
          GitSyncBranchContext.builder()
              .gitBranchInfo(GitEntityInfo.builder().branch(baseBranch).yamlGitConfigId(repoIdentifier).build())
              .build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(branchContext, true)) {
        return findAllReferredInputSetsInternal(
            inputSetService, referencesInOverlay, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      }
    } else {
      return findAllReferredInputSetsInternal(
          inputSetService, referencesInOverlay, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
  }

  // todo: optimise this
  private List<Optional<InputSetEntity>> findAllReferredInputSetsInternal(PMSInputSetService inputSetService,
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
      GitSyncSdkService gitSyncSdkService, PMSInputSetService inputSetService, InputSetEntity inputSetEntity) {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = inputSetEntity.getPipelineIdentifier();
    String yaml = inputSetEntity.getYaml();

    List<String> currentReferences = InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml);
    List<Optional<InputSetEntity>> inputSets = findAllReferredInputSetsInternal(
        inputSetService, currentReferences, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    Set<String> invalidReferences =
        InputSetErrorsHelper.getInvalidInputSetReferences(inputSets, currentReferences).keySet();
    List<String> validReferences =
        currentReferences.stream().filter(ref -> !invalidReferences.contains(ref)).collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(validReferences)) {
      String newYaml = InputSetYamlHelper.setReferencesFromOverlayInputSetYaml(yaml, validReferences);
      return InputSetYamlDiffDTO.builder()
          .oldYAML(yaml)
          .newYAML(newYaml)
          .isInputSetEmpty(false)
          .noUpdatePossible(false)
          .build();
    }

    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.INPUT_SET, null, false);
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      criteria = criteria.and(InputSetEntityKeys.branch)
                     .is(gitEntityInfo.getBranch())
                     .and(InputSetEntityKeys.yamlGitConfigRef)
                     .is(gitEntityInfo.getYamlGitConfigId());
    }
    boolean hasInputSets = EmptyPredicate.isNotEmpty(inputSetService.list(criteria));
    if (hasInputSets) {
      return InputSetYamlDiffDTO.builder().isInputSetEmpty(true).noUpdatePossible(false).build();
    } else {
      return InputSetYamlDiffDTO.builder().isInputSetEmpty(true).noUpdatePossible(true).build();
    }
  }
}
