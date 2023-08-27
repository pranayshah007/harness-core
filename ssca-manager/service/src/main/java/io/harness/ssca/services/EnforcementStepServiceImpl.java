/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.ArtifactRepository;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.spec.server.ssca.v1.model.EnforceSbomResponseBody;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
import io.harness.ssca.beans.RuleDTO;
import io.harness.ssca.enforcement.ExecutorRegistry;
import io.harness.ssca.enforcement.constants.RuleExecutorType;
import io.harness.ssca.enforcement.rule.Engine;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.NotFoundException;

public class EnforcementStepServiceImpl implements EnforcementStepService {
  @Inject ArtifactRepository artifactRepository;
  @Inject ExecutorRegistry executorRegistry;
  @Inject RuleEngineService ruleEngineService;
  @Inject EnforcementSummaryService enforcementSummaryService;

  @Override
  public EnforceSbomResponseBody enforceSbom(
      String accountId, String orgIdentifier, String projectIdentifier, EnforceSbomRequestBody body) {
    ArtifactEntity artifactEntity = artifactRepository.findFirstByUrl(body.getArtifact().getRegistryUrl()).get();

    RuleDTO ruleDTO = ruleEngineService.getRules(accountId, orgIdentifier, projectIdentifier, body.getPolicyFileId());

    Engine engine = Engine.builder()
                        .artifact(artifactEntity)
                        .enforcementId(body.getEnforcementId())
                        .executorRegistry(executorRegistry)
                        .executorType(RuleExecutorType.MONGO_EXECUTOR)
                        .rules(ruleDTO.getDenyList())
                        .build();

    List<EnforcementResultEntity> denyListResult = engine.executeRules();

    engine.setRules(ruleDTO.getAllowList());
    List<EnforcementResultEntity> allowListResult = engine.executeRules();

    String status = enforcementSummaryService.persistEnforcementSummary(
        body.getEnforcementId(), denyListResult, allowListResult, artifactEntity);

    EnforceSbomResponseBody responseBody = new EnforceSbomResponseBody();
    responseBody.setEnforcementId(body.getEnforcementId());
    responseBody.setStatus(status);

    return responseBody;
  }

  @Override
  public EnforcementSummaryResponse getEnforcementSummary(
      String accountId, String orgIdentifier, String projectIdentifier, String enforcementId) {
    EnforcementSummaryEntity enforcementSummary =
        enforcementSummaryService.getEnforcementSummary(accountId, orgIdentifier, projectIdentifier, enforcementId)
            .orElseThrow(()
                             -> new NotFoundException(String.format(
                                 "Enforcement with enforcementIdentifier [%s] is not found", enforcementId)));

    return new EnforcementSummaryResponse()
        .enforcementId(enforcementSummary.getEnforcementId())
        .artifact(new Artifact()
                      .id(enforcementSummary.getArtifact().getArtifactId())
                      .name(enforcementSummary.getArtifact().getName())
                      .type(enforcementSummary.getArtifact().getType())
                      .registryUrl(enforcementSummary.getArtifact().getUrl())
                      .tag(enforcementSummary.getArtifact().getTag())

                )
        .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
        .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
        .status(enforcementSummary.getStatus());
  }
}
