/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateSetupDetails implements YamlDTO {
  private String orgIdentifier;
  private String projectIdentifier;
  @NotNull private String name;
  private String description;
  private DelegateSize size;
  private String hostName;
  // TODO: Remove delegateCongigId once we drop this from UI.
  private String delegateConfigurationId;
  // This can be blank also, since we can create a group from delegate yaml itself.
  // Custom limit for delegate-name as 63 characters because kubernetes component name can be at most 63 characters.
  @EntityIdentifier(allowBlank = true, maxLength = 63) private String identifier;

  private K8sConfigDetails k8sConfigDetails;

  private Set<String> tags;

  @Schema(description = "Currently KUBERNETES and HELM_DELEGATE are supported.") @NotNull private String delegateType;
  private String tokenName;
  private Boolean runAsRoot;
}
