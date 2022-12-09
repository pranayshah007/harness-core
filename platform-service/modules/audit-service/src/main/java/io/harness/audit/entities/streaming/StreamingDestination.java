/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.entities.streaming;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@FieldNameConstants(innerTypeName = "StreamingDestinationKeys")
@StoreIn(DbAliases.AUDITS)
@Entity(value = "streamingDestinations", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("streamingDestinations")
public abstract class StreamingDestination {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_identifier_unique_index")
                 .field(StreamingDestinationKeys.accountIdentifier)
                 .field(StreamingDestinationKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status_index")
                 .field(StreamingDestinationKeys.accountIdentifier)
                 .field(StreamingDestinationKeys.status)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotBlank @EntityIdentifier String identifier;
  @Trimmed @NotBlank String accountIdentifier;
  @Trimmed @NotBlank @NGEntityName String name;
  @NotNull StatusEnum status;
  @NotBlank String connectorRef;
  @NotNull StreamingDestinationSpecDTO.TypeEnum type;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedDate;
}
