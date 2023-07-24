/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean;
import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Data
@Builder
@FieldNameConstants(innerTypeName = "PollingDocumentKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "pollingDocuments", noClassnameStored = true)
@Document("pollingDocuments")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDC)
public class PollingDocument implements PersistentEntity, AccountAccess, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_pollingType_pollingItem")
                 .field(PollingDocumentKeys.accountId)
                 .field(PollingDocumentKeys.orgIdentifier)
                 .field(PollingDocumentKeys.projectIdentifier)
                 .field(PollingDocumentKeys.pollingType)
                 .field(PollingDocumentKeys.pollingInfo)
                 .field(PollingDocumentKeys.signatures)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_pollingInfo.connectorRef")
                 .field(PollingDocumentKeys.accountId)
                 .field(PollingDocumentKeys.pollingInfo + "." + YAMLFieldNameConstants.CONNECTOR_REF)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_signatures")
                 .field(PollingDocumentKeys.accountId)
                 .field(PollingDocumentKeys.signatures)
                 .build())

        .build();
  }

  @Id @dev.morphia.annotations.Id private String uuid;

  @NotNull private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  @NotNull private List<String> signatures;
  private Map<String, List<String>> signaturesLock; // Used for MultiRegionArtifact triggers.

  @JsonProperty("type") private PollingType pollingType;

  private PollingInfo pollingInfo;

  private PolledResponse polledResponse;

  private String perpetualTaskId;

  private int failedAttempts;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
}
