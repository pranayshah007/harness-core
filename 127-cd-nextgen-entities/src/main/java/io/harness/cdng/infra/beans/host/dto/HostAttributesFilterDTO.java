/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans.host.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@OwnedBy(CDP)
@Builder
@RecasterAlias("io.harness.cdng.infra.beans.host.dto.HostAttributesFilterDTO")
public class HostAttributesFilterDTO implements HostFilterSpecDTO {
  Map<String, String> value;

  @Override
  @JsonIgnore
  public HostFilterType getType() {
    return HostFilterType.HOST_ATTRIBUTES;
  }

  // customizing Lombok builder
  public static class HostAttributesFilterDTOBuilder {
    private Map<String, String> value;

    public HostAttributesFilterDTOBuilder value(ParameterField<?> parameterField) {
      Map<String, String> hostAttributesValue = ParameterFieldHelper.getParameterFieldMapValueBySeparator(
          parameterField, HOSTS_SEPARATOR, HOST_ATTRIBUTES_SEPARATOR);
      this.value = isEmpty(hostAttributesValue) ? Collections.emptyMap() : hostAttributesValue;
      return this;
    }
  }
}
