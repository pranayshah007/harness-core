/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.filter;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.collect.ImmutableMap;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncFilterCreationDispatcher implements Runnable {
  private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  private PMSPipelineService pmsPipelineService;
  private String uuid;
  private Integer yamlHash;
  private String messageId;

  @Builder
  public AsyncFilterCreationDispatcher(PMSPipelineServiceHelper pmsPipelineServiceHelper,
      PMSPipelineService pmsPipelineService, String uuid, Integer yamlHash, String messageId) {
    this.pmsPipelineServiceHelper = pmsPipelineServiceHelper;
    this.pmsPipelineService = pmsPipelineService;
    this.uuid = uuid;
    this.yamlHash = yamlHash;
    this.messageId = messageId;
  }

  @Override
  public void run() {
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipelineByUUID(uuid);
    PipelineEntity updatedPipelineEntity;
    if (pipelineEntity.isPresent()) {
      if (Objects.equals(pipelineEntity.get().getYamlHash(), yamlHash)) {
        try (AutoLogContext ignore =
                 new AutoLogContext(ImmutableMap.of("pipelineId", pipelineEntity.get().getIdentifier(), "messageId",
                                        messageId, "yamlHash", String.valueOf(yamlHash)),
                     AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
          log.info("Start processing async filter creation via dispatcher for pipelineId: {}",
              pipelineEntity.get().getIdentifier());
          try {
            SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
            SourcePrincipalContextBuilder.setSourcePrincipal(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
            updatedPipelineEntity = pmsPipelineServiceHelper.updatePipelineInfo(
                pipelineEntity.get(), pipelineEntity.get().getHarnessVersion());

            // Again check if yaml hash matches or not before updating as other thread can update filters already.
            PipelineEntity updatedResult =
                pmsPipelineServiceHelper.updatePipelineFilters(updatedPipelineEntity, uuid, yamlHash);
            if (updatedResult == null) {
              throw new InvalidRequestException(String.format(
                  "Pipeline [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                  pipelineEntity.get().getIdentifier(), pipelineEntity.get().getProjectIdentifier(),
                  pipelineEntity.get().getOrgIdentifier()));
            }
          } catch (Exception e) {
            log.error(
                String.format("Async Filter creation failed for Pipeline %s in account - %s, org - %s, project - %s",
                    pipelineEntity.get().getIdentifier(), pipelineEntity.get().getAccountId(),
                    pipelineEntity.get().getOrgIdentifier(), pipelineEntity.get().getProjectIdentifier()),
                e);
          }
        }
      }
    }
  }
}
