/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityName;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("TemplateMetadataSummaryResponse")
@Schema(name = "TemplateMetadataSummaryResponse",
    description = "This contains details of the Template Metadata Summary Response")
@OwnedBy(CDC)
public class TemplateMetadataSummaryResponseDTO {
  String accountId;

  String orgIdentifier;
  String projectIdentifier;
  String identifier;

  @EntityName String name;
  @Size(max = 1024) String description;
  Map<String, String> tags;

  String versionLabel;
  Boolean stableTemplate;

  TemplateEntityType templateEntityType;
  String childType;

  Scope templateScope;
  Long version;
  EntityGitDetails gitDetails;
  Long lastUpdatedAt;
  Long createdAt;
  StoreType storeType;
  String connectorRef;
  String icon;
  String yamlVersion;
}
