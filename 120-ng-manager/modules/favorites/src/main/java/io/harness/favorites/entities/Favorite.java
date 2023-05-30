/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.entities;

import io.harness.ModuleType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.Trimmed;
import io.harness.favorites.ResourceType;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "FavoriteKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "favorites", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("favorites")
@Persistent
@TypeAlias("Favorite")
public class Favorite {
  @NotEmpty @EntityIdentifier String userIdentifier;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @NotEmpty ModuleType module;
  @NotEmpty ResourceType resourceType;
  @NotEmpty @Trimmed String resourceIdentifier;
  @CreatedDate Long created;
}
