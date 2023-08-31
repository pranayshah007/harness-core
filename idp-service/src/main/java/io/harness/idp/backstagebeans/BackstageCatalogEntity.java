/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstagebeans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BackstageCatalogApiEntity.class, name = "Api")
  , @JsonSubTypes.Type(value = BackstageCatalogComponentEntity.class, name = "Component"),
      @JsonSubTypes.Type(value = BackstageCatalogDomainEntity.class, name = "Domain"),
      @JsonSubTypes.Type(value = BackstageCatalogGroupEntity.class, name = "Group"),
      @JsonSubTypes.Type(value = BackstageCatalogLocationEntity.class, name = "Location"),
      @JsonSubTypes.Type(value = BackstageCatalogResourceEntity.class, name = "Resource"),
      @JsonSubTypes.Type(value = BackstageCatalogSystemEntity.class, name = "System"),
      @JsonSubTypes.Type(value = BackstageCatalogTemplateEntity.class, name = "Template"),
      @JsonSubTypes.Type(value = BackstageCatalogUserEntity.class, name = "User")
})
public abstract class BackstageCatalogEntity {
  private String apiVersion = "backstage.io/v1alpha1";
  private Metadata metadata;
  @JsonIgnore private String kind;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Metadata {
    @JsonIgnore private String identifier;
    @JsonIgnore private String absoluteIdentifier;
    private String name;
    private String namespace;
    private String description;
    private List<String> tags;
    private String uid;
    @JsonInclude(JsonInclude.Include.NON_EMPTY) private Map<String, String> annotations;

    public void setMetadata(String identifier, String absoluteIdentifier, String name, String description,
        List<String> tags, Map<String, String> annotations) {
      this.identifier = identifier;
      this.absoluteIdentifier = absoluteIdentifier;
      this.name = name;
      this.description = description;
      this.tags = tags;
      this.annotations = annotations;
    }
  }
}
