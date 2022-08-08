/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.aws;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
public class AwsCliStsAssumeRoleCommandOutputSchema {
  @JsonProperty("Credentials") private Credentials credentials;
  @JsonProperty("AssumedRoleUser") private AssumeRole assumeRole;

  @Data
  @Builder
  public static class Credentials {
    @JsonProperty("AccessKeyId") private String accessKeyId;
    @JsonProperty("SecretAccessKey") private String secretAccessKey;
    @JsonProperty("SessionToken") private String sessionToken;
    @JsonProperty("Expiration") private String expiration;
  }

  @Data
  @Builder
  public static class AssumeRole {
    @JsonProperty("AssumedRoleId") private String assumeRoleId;
    @JsonProperty("Arn") private String arn;
  }
}
