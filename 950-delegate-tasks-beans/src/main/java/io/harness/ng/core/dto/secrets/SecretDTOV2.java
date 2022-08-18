/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.mapper.TagMapper.convertToList;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.ng.core.models.Secret;
import io.harness.secretmanagerclient.SecretType;
import io.harness.security.dto.Principal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Schema(name = "Secret", description = "This is details of the secret entity defined in Harness.")
public class SecretDTOV2 {
  @NotNull @Schema(description = "This specifies the type of secret") private SecretType type;
  @NotNull @NGEntityName @Schema(description = "Name of the Secret") private String name;
  @NotNull @EntityIdentifier @Schema(description = "Identifier of the Secret") private String identifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  private String orgIdentifier;
  @EntityIdentifier(allowBlank = true)
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  private String projectIdentifier;
  @Schema(description = "Tags") private Map<String, String> tags;
  @Schema(description = "Description of the Secret") private String description;
  @JsonIgnore private Principal owner;

  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
  @Valid
  @NotNull
  private SecretSpecDTO spec;

  @Builder
  public SecretDTOV2(SecretType type, String name, String description, String identifier, String orgIdentifier,
      String projectIdentifier, Map<String, String> tags, SecretSpecDTO spec) {
    this.type = type;
    this.name = name;
    this.description = description;
    this.identifier = identifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.tags = tags;
    this.spec = spec;
  }

  public Secret toEntity() {
    return Secret.builder()
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .identifier(getIdentifier())
        .description(getDescription())
        .name(getName())
        .tags(convertToList(getTags()))
        .type(getType())
        .secretSpec(Optional.ofNullable(getSpec()).map(SecretSpecDTO::toEntity).orElse(null))
        .owner(getOwner())
        .build();
  }
}
