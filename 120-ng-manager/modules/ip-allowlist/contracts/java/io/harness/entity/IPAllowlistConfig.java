/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "IPAllowlistConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "ipAllowlist", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("ipAllowlist")
@Persistent
@OwnedBy(HarnessTeam.PL)
public class IPAllowlistConfig {
  @Id String id;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed @NotEmpty String identifier;
  @Trimmed @NotEmpty String name;
  @NotEmpty @Indexed(unique = true) String fullyQualifiedIdentifier;
  String description;
  @Singular @Size(max = 128) List<NGTag> tags;
  @Builder.Default Boolean enabled = Boolean.FALSE;
  @Builder.Default Set<AllowedSourceType> allowedSourceType = Set.of(AllowedSourceType.API, AllowedSourceType.UI);
  String value;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
}
