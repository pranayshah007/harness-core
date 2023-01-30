/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.tas.service.TasResourceServiceImpl;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.pcf.CfInfraMappingDataResult;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponseNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasResourceServiceImplTest extends CDNGTestBase {
  @Mock TasEntityHelper tasEntityHelper;
  @InjectMocks private TasResourceServiceImpl tasResourceService;

  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String CONNECTOR = "Connector";

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetOrganization() {
    CfInfraMappingDataResponseNG delegateResponse =
        CfInfraMappingDataResponseNG.builder()
            .cfInfraMappingDataResult(CfInfraMappingDataResult.builder()
                                          .organizations(new ArrayList<String>(Collections.singleton("org1")))
                                          .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    doReturn(delegateResponse).when(tasEntityHelper).executeSyncTask(any(), any(), any());
    doReturn(ConnectorInfoDTO.builder().build()).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    List<String> orgList = tasResourceService.listOrganizations(CONNECTOR, ACCOUNT, ORG, PROJECT);
    assertThat(orgList.get(0)).isEqualTo("org1");
  }
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetSpaces() {
    CfInfraMappingDataResponseNG delegateResponse =
        CfInfraMappingDataResponseNG.builder()
            .cfInfraMappingDataResult(CfInfraMappingDataResult.builder()
                                          .spaces(new ArrayList<String>(Collections.singleton("space1")))
                                          .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    doReturn(delegateResponse).when(tasEntityHelper).executeSyncTask(any(), any(), any());
    doReturn(ConnectorInfoDTO.builder().build()).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    List<String> spaceList = tasResourceService.listSpaces(CONNECTOR, ACCOUNT, ORG, PROJECT, "org");
    assertThat(spaceList.get(0)).isEqualTo("space1");
  }
}
