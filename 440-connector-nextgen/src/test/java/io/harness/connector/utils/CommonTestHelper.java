/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.utils;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;

@UtilityClass
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
@OwnedBy(CE)
public class CommonTestHelper {
  public String connectorIdentifier = "identifier_ph";
  public String name = "name_ph";
  public String description = "description_ph";
  public String accountIdentifier = "accountIdentifier_ph";
  public String projectIdentifier = "projectIdentifier_ph";
  public String orgIdentifier = "orgIdentifier_ph";
  public Map<String, String> tags = ImmutableMap.of("company", "Harness", "env", "dev");

  public ConnectorDTO createConnectorDTO(ConnectorType connectorType, ConnectorConfigDTO connectorConfigDTO) {
    final ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                                  .connectorConfig(connectorConfigDTO)
                                                  .connectorType(connectorType)
                                                  .description(description)
                                                  .identifier(connectorIdentifier)
                                                  .name(name)
                                                  .accountIdentifier(accountIdentifier)
                                                  .orgIdentifier(orgIdentifier)
                                                  .projectIdentifier(projectIdentifier)
                                                  .tags(tags)
                                                  .build();
    return ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();
  }
}
