/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class AWSTemporaryCredentials {
  @JsonProperty("Code") String code;
  @JsonProperty("LastUpdated") String lastUpdated;
  @JsonProperty("Type") String type;
  @JsonProperty("AccessKeyId") String accessKeyId;
  @JsonProperty("SecretAccessKey") String secretKey;
  @JsonProperty("Token") String token;
  @JsonProperty("Expiration") String expiration;
}
