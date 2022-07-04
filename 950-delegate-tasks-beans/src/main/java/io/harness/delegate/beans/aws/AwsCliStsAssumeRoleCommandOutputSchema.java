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
