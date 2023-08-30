/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.template;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.EntityName;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("TemplateResponse")
@Schema(name = "TemplateResponse", description = "This contains details of the Template Response")
@EqualsAndHashCode
public class TemplateResponseDTO {
  @NotNull @NotEmpty String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @NotNull @NotEmpty String identifier;

  @NotNull @EntityName String name;
  @Size(max = 1024) String description;
  Map<String, String> tags;

  @NotEmpty String yaml;

  String versionLabel;
  boolean isStableTemplate;

  TemplateEntityType templateEntityType;
  String childType;

  Scope templateScope;
  Long version;
  EntityGitDetails gitDetails;
  EntityValidityDetails entityValidityDetails;
  long lastUpdatedAt;
  StoreType storeType;
  String connectorRef;
  String icon;
  CacheResponseMetadataDTO cacheResponseMetadata;
}
