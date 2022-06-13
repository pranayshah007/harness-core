package io.harness.delegate.beans.connector.scm.github;

import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@JsonIgnoreProperties({"userName"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GithubOauth")
@Schema(name = "GithubOauth", description = "This contains details of the Github credentials Specs for oauth")
public class GithubOauthDTO implements GithubHttpCredentialsSpecDTO, GithubApiAccessSpecDTO {
  public static String userName = "Oauth";
  @NotNull @ApiModelProperty(dataType = "string") @SecretReference SecretRefData tokenRef;
}
