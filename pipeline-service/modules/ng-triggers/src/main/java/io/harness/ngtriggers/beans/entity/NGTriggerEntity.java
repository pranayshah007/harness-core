/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.iterator.PersistentNGCronIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "NGTriggerEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "triggersNG", noClassnameStored = true)
@Document("triggersNG")
@TypeAlias("triggersNG")
@HarnessEntity(exportable = true)
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerEntity implements PersistentEntity, PersistentNGCronIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(
            CompoundMongoIndex.builder()
                .name(
                    "unique_accountId_organizationIdentifier_projectIdentifier_targetIdentifier_triggerType_identifier")
                .unique(true)
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.orgIdentifier)
                .field(NGTriggerEntityKeys.projectIdentifier)
                .field(NGTriggerEntityKeys.targetIdentifier)
                .field(NGTriggerEntityKeys.targetType)
                .field(NGTriggerEntityKeys.identifier)
                .build(),
            CompoundMongoIndex.builder()
                .name("unique_accountId_organizationIdentifier_projectIdentifier_identifier")
                .unique(false)
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.orgIdentifier)
                .field(NGTriggerEntityKeys.projectIdentifier)
                .field(NGTriggerEntityKeys.identifier)
                .build(),
            CompoundMongoIndex.builder()
                .name("type_repoUrl")
                .field(NGTriggerEntityKeys.type)
                .field("metadata.webhook.git.connectorIdentifier")
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.orgIdentifier)
                .field(NGTriggerEntityKeys.projectIdentifier)
                .build(),
            CompoundMongoIndex.builder()
                .name("accId_sourcerepo_index")
                .field(NGTriggerEntityKeys.accountId)
                .field("metadata.webhook.type")
                .build(),
            CompoundMongoIndex.builder()
                .name("accId_signature_index")
                .field(NGTriggerEntityKeys.accountId)
                .field("metadata.buildMetadata.pollingConfig.signature")
                .build(),
            CompoundMongoIndex.builder()
                .name("webhookToken_index")
                .field(NGTriggerEntityKeys.customWebhookToken)
                .build(),
            CompoundMongoIndex.builder()
                .name("accId_signature_index_for_multibuildmetadata")
                .field(NGTriggerEntityKeys.accountId)
                .field("metadata.multiBuildMetadata.pollingConfig.signature")
                .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String uuid;
  @EntityName String name;
  @EntityIdentifier @NotEmpty String identifier;
  @Size(max = 1024) String description;
  @NotEmpty String yaml;
  @NotEmpty NGTriggerType type;
  String status;
  TriggerStatus triggerStatus;
  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @NotEmpty String projectIdentifier;
  @NotEmpty String targetIdentifier;
  @NotEmpty TargetType targetType;

  @NotEmpty NGTriggerMetadata metadata;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;
  @Builder.Default Boolean withServiceV2 = Boolean.FALSE;
  @Singular @Size(max = 128) List<NGTag> tags;
  @Builder.Default Boolean enabled = Boolean.TRUE;
  String pollInterval;
  String webhookId;
  String customWebhookToken;
  String encryptedWebhookSecretIdentifier;
  List<String> stagesToExecute;
  @FdIndex private List<Long> nextIterations; // List of activation times for cron triggers
  @Builder.Default Long ymlVersion = Long.valueOf(3);

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    if (metadata.getCron() == null || nextIterations == null) {
      return new ArrayList<>();
    }
    try {
      String cronExpr = metadata.getCron().getExpression();
      String cronType = StringUtils.isBlank(metadata.getCron().getType()) ? "UNIX" : metadata.getCron().getType();
      expandNextIterations(skipMissed, throttled, cronExpr, nextIterations, cronType);
    } catch (Exception e) {
      log.error("Failed to schedule executions for trigger {}", uuid, e);
      throw e;
    }
    return nextIterations;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (metadata.getCron() == null || nextIterations == null) {
      return null;
    }
    return nextIterations.get(0);
  }

  public Boolean getWithServiceV2() {
    return withServiceV2 != null && withServiceV2;
  }
}
