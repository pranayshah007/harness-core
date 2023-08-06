/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.pdcconnector.outcome.HostOutcomeDTO;
import io.harness.delegate.beans.connector.pdcconnector.outcome.PhysicalDataCenterConnectorOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@Schema(name = "PhysicalDataCenterConnectorDTO", description = "This contains Physical Data Center connector details")
@RecasterAlias("io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO")
public class PhysicalDataCenterConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @JsonDeserialize(using = HostDTOsDeserializer.class) @JsonProperty("hosts") @Valid List<HostDTO> hosts;
  Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return PhysicalDataCenterConnectorOutcomeDTO.builder()
        .hosts(hosts.stream()
                   .map(hostDTO
                       -> HostOutcomeDTO.builder()
                              .hostAttributes(hostDTO.getHostAttributes())
                              .hostname(hostDTO.getHostName())
                              .build())
                   .collect(Collectors.toList()))
        .delegateSelectors(this.delegateSelectors)
        .build();
  }
}
