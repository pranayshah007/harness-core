package io.harness.delegate.beans.connector.scm.bitbucket;

import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.validation.OneOfField;

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
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("BitbucketOauth")
@Schema(name = "BitbucketOauth",
    description =
        "This contains details of the information such as references of tokens needed for bitbucket API access")
public class BitbucketOauthDTO implements BitbucketHttpCredentialsSpecDTO, BitbucketApiAccessSpecDTO {
  public static String userName = "oauth2";
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData tokenRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData refreshTokenRef;
}
