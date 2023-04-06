/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class BackstageCatalogEntity {
  private String apiVersion = "backstage.io/v1alpha1";
  private Metadata metadata;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Metadata {
    @JsonIgnore private String identifier;
    @JsonIgnore private String absoluteIdentifier;
    private String name;
    private String description;
    private List<String> tags;
    @JsonInclude(JsonInclude.Include.NON_EMPTY) private Map<String, String> annotations;

    public void setMetadata(String identifier, String absoluteIdentifier, String name, String description,
        List<String> tags, Map<String, String> annotations) {
      this.absoluteIdentifier = absoluteIdentifier;
      this.identifier = identifier;
      this.name = name;
      this.description = description;
      this.tags = tags;
      this.annotations = annotations;
    }
  }
}
