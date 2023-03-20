/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.TRIGGER;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.WorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.TriggerSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.NgMigrationService;

import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class TriggerMigrationService extends NgMigrationService {
  @Inject TriggerService triggerService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new NotImplementedException("trigger is not implemented exception");
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> summaryByType =
        entities.stream()
            .map(entity -> (Trigger) entity.getEntity())
            .collect(groupingBy(entity -> entity.getCondition().getConditionType().name(), counting()));
    return TriggerSummary.builder().count(entities.size()).typeSummary(summaryByType).build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    Trigger trigger = (Trigger) entity;
    String entityId = trigger.getUuid();
    CgEntityId triggerEntityId = CgEntityId.builder().type(TRIGGER).id(entityId).build();
    CgEntityNode triggerNode =
        CgEntityNode.builder().id(entityId).type(TRIGGER).entityId(triggerEntityId).entity(trigger).build();
    Set<CgEntityId> children = new HashSet<>();

    // Add workflow/pipeline to discovery
    children.add(
        CgEntityId.builder()
            .id(trigger.getWorkflowId())
            .type(WorkflowType.ORCHESTRATION.equals(trigger.getWorkflowType()) ? NGMigrationEntityType.WORKFLOW
                                                                               : NGMigrationEntityType.PIPELINE)
            .build());

    // Add artifact source
    if (trigger.getCondition() != null
        && TriggerConditionType.NEW_ARTIFACT.equals(trigger.getCondition().getConditionType())) {
      ArtifactTriggerCondition condition = (ArtifactTriggerCondition) trigger.getCondition();
      if (StringUtils.isNotBlank(condition.getArtifactStreamId())) {
        children.add(CgEntityId.builder()
                         .id(condition.getArtifactStreamId())
                         .type(NGMigrationEntityType.ARTIFACT_STREAM)
                         .build());
      }
    }

    if (trigger.getCondition() != null
        && TriggerConditionType.WEBHOOK.equals(trigger.getCondition().getConditionType())) {
      WebHookTriggerCondition condition = (WebHookTriggerCondition) trigger.getCondition();
      if (StringUtils.isNotBlank(condition.getGitConnectorId())) {
        children.add(
            CgEntityId.builder().type(NGMigrationEntityType.CONNECTOR).id(condition.getGitConnectorId()).build());
      }
    }

    return DiscoveryNode.builder().children(children).entityNode(triggerNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(triggerService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return null;
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    return null;
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return false;
  }
}
