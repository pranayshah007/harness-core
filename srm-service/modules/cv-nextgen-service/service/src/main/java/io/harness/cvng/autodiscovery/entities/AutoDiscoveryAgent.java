/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.autodiscovery.entities;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AutoDiscoveryAgentKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CVNG)
@Entity(value = "autoDiscoveryAgents")
@HarnessEntity(exportable = true)
@OwnedBy(CV)
public class AutoDiscoveryAgent implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_agent_idx")
                 .unique(true)
                 .field(AutoDiscoveryAgentKeys.accountId)
                 .field(AutoDiscoveryAgentKeys.orgIdentifier)
                 .field(AutoDiscoveryAgentKeys.projectIdentifier)
                 .field(AutoDiscoveryAgentKeys.agentIdentifier)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotNull String accountId;
  @EntityIdentifier String orgIdentifier;
  @EntityIdentifier String projectIdentifier;
  private long lastUpdatedAt;
  private long createdAt;

  String agentIdentifier;
}
