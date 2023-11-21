/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.entities.developer;

import io.harness.ModuleType;
import io.harness.SecondaryEntitlement;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.GTM)
@Data
@Builder
@FieldNameConstants(innerTypeName = "DeveloperMappingKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "developerMappings", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("developerMappings")
@Persistent
public class DeveloperMapping implements PersistentEntity, NGAccountAccess {
  @Id protected String id;
  @Trimmed @NotEmpty protected String accountIdentifier;
  @NotEmpty protected ModuleType moduleType;
  @NotEmpty protected int developerCount;
  @NotEmpty protected SecondaryEntitlement secondaryEntitlement;
  @NotEmpty protected int secondaryEntitlementCount;
  @CreatedBy EmbeddedUser createdBy;
  @CreatedDate Long createdAt;
  @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @LastModifiedDate Long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_moduleType_secondaryEntitlement_query_index")
                 .field(DeveloperMappingKeys.accountIdentifier)
                 .field(DeveloperMappingKeys.moduleType)
                 .field(DeveloperMappingKeys.secondaryEntitlement)
                 .build())
        .build();
  }
}
