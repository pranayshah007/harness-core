/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.tunnel.entities;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TunnelKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "tunnel", noClassnameStored = true)
@Document("tunnel")
@Persistent
@OwnedBy(CI)
public class Tunnel implements UuidAware, PersistentEntity {
  @Id @dev.morphia.annotations.Id protected String uuid;
  @NotEmpty @Trimmed protected String accountIdentifier;
  @NotEmpty protected String port;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("tunnelAccountIdIdx")
                 .unique(true)
                 .field(TunnelKeys.accountIdentifier)
                 .build())
        .build();
  }
}
