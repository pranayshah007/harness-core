/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.prometheusconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
public class PrometheusConnectorDTO extends ConnectorConfigDTO implements DecryptableEntity, DelegateSelectable {
  @NotNull @NotBlank String url;
  String username;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData passwordRef;
  List<CustomHealthKeyAndValue> headers;

  Set<String> delegateSelectors;

  public String getUrl() {
    return url.endsWith("/") ? url : url + "/";
  }
  public List<CustomHealthKeyAndValue> getHeaders() {
    if (headers == null) {
      return Collections.emptyList();
    }
    return headers;
  }
  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<CustomHealthKeyAndValue> headerList = headers != null ? headers : new LinkedList();
    List<DecryptableEntity> decryptableEntities = new ArrayList<>();
    decryptableEntities.addAll(
        headerList.stream().filter(keyAndValue -> keyAndValue.isValueEncrypted()).collect(Collectors.toList()));
    decryptableEntities.add(this);
    return decryptableEntities;
  }
}
