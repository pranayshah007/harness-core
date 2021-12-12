package io.harness.delegate.beans.connector.customhealthconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
@OwnedBy(HarnessTeam.CV)
public class CustomHealthConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @NotNull @NotBlank String baseURL;
  List<CustomHealthKeyAndValue> headers;
  List<CustomHealthKeyAndValue> params;
  @NotNull CustomHealthMethod method;
  String validationBody;
  String validationPath;
  Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<CustomHealthKeyAndValue> headerList = headers != null ? headers : new LinkedList();
    List<CustomHealthKeyAndValue> paramList = params != null ? params : new LinkedList();
    return Stream.concat(headerList.stream(), paramList.stream())
        .filter(keyAndValue -> keyAndValue.isValueEncrypted())
        .collect(Collectors.toList());
  }

  public List<CustomHealthKeyAndValue> getHeaders() {
    return headers == null ? Collections.EMPTY_LIST : headers;
  }

  public List<CustomHealthKeyAndValue> getParams() {
    return params == null ? Collections.EMPTY_LIST : params;
  }

  public String getBaseURL() {
    return baseURL.endsWith("/") ? baseURL : baseURL + "/";
  }
}
