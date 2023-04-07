/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ZendeskDescription")
public class ZendeskDescription {
  @NotNull String message;
  @NotNull String url;
  @NotNull String userBrowser;
  @NotNull String browserResolution;
  @NotNull String userOS;
  @NotNull String website;
  @NotNull String userName;
  @NotNull String accountId;
  @NotNull String module;
}
