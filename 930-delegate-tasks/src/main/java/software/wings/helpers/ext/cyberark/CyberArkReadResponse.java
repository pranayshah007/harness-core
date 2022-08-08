/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.cyberark;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * @author marklu on 2019-08-01
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@OwnedBy(PL)
public class CyberArkReadResponse {
  @JsonProperty("Name") private String name;
  @JsonProperty("UserName") private String userName;
  @JsonProperty("Content") private String content;
  @JsonProperty("Folder") private String folder;
  @JsonProperty("Safe") private String safe;
  @JsonProperty("Address") private String address;
  @JsonProperty("LogonDomain") private String logonDomain;
  @JsonProperty("DeviceType") private String deviceType;
  @JsonProperty("CreationMethod") private String creationMethod;
  @JsonProperty("PasswordChangeInProcess") private String passwordChangeInProcess;
}
