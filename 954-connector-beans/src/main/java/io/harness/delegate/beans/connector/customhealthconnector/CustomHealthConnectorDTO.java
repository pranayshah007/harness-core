/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.customhealthconnector;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;

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
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CV)
public class CustomHealthConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @URL @NotNull @NotBlank String baseURL;
  List<CustomHealthKeyAndValue> headers;
  List<CustomHealthKeyAndValue> params;
  @NotNull CustomHealthMethod method;
  String validationBody;
  String validationPath;
  Set<String> delegateSelectors;
  @Builder.Default private ConnectorType connectorType = ConnectorType.CUSTOM_HEALTH;

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
    return isEmpty(baseURL) || baseURL.endsWith("/") ? baseURL : baseURL + "/";
  }

  public String getValidationPath() {
    return isNotEmpty(validationPath) && validationPath.charAt(0) == '/' ? validationPath.substring(1) : validationPath;
  }
}
