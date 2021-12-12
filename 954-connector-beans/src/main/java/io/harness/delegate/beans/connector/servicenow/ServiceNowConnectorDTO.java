package io.harness.delegate.beans.connector.servicenow;

import io.harness.beans.DecryptableEntity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.exception.InvalidRequestException;
import io.harness.validation.OneOfField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("ServiceNowConnector")
@OneOfField(fields = {"username", "usernameRef"})
public class ServiceNowConnectorDTO extends ConnectorConfigDTO {
  @NotNull String serviceNowUrl;
  @NotBlank String username;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData usernameRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData passwordRef;
  Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public void validate() {
    Preconditions.checkState(EmptyPredicate.isNotEmpty(serviceNowUrl), "ServiceNow URL cannot be empty");
    if (EmptyPredicate.isEmpty(username) && (usernameRef == null || usernameRef.isNull())) {
      throw new InvalidRequestException("Username cannot be empty");
    }
    if (EmptyPredicate.isNotEmpty(username) && usernameRef != null && !usernameRef.isNull()) {
      throw new InvalidRequestException("Only one of username or usernameRef can be provided");
    }
  }
}
