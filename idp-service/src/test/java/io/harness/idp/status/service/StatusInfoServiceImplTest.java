/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.service;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.repositories.StatusInfoRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.StatusInfo;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class StatusInfoServiceImplTest {
  @InjectMocks private StatusInfoServiceImpl statusInfoServiceImpl;
  @Mock private StatusInfoRepository statusInfoRepository;

  private static final String ACCOUNT_ID = "123";
  private String type;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndType() {
    type = StatusType.ONBOARDING.toString();
    StatusInfoEntity statusInfoEntity = initializeStatusInfoEntity();
    when(statusInfoRepository.findByAccountIdentifierAndType(ACCOUNT_ID, type))
        .thenReturn(Optional.of(statusInfoEntity));
    Optional<StatusInfo> statusInfo = statusInfoServiceImpl.findByAccountIdentifierAndType(ACCOUNT_ID, type);
    assertEquals(statusInfo.get().getCurrentStatus(), StatusInfo.CurrentStatusEnum.COMPLETED);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSave() {
    type = StatusType.ONBOARDING.toString();
    StatusInfoEntity statusInfoEntity = initializeStatusInfoEntity();
    when(statusInfoRepository.saveOrUpdate(any())).thenReturn(statusInfoEntity);
    StatusInfo statusInfo = new StatusInfo();
    statusInfo.setCurrentStatus(statusInfoEntity.getStatus());
    statusInfo.setReason(statusInfoEntity.getReason());
    statusInfo.setUpdatedAt(statusInfoEntity.getLastModifiedAt());
    StatusInfo statusInfo1 = statusInfoServiceImpl.save(statusInfo, ACCOUNT_ID, type);
    assertEquals(statusInfo1.getCurrentStatus(), StatusInfo.CurrentStatusEnum.COMPLETED);
  }

  StatusInfoEntity initializeStatusInfoEntity() {
    return StatusInfoEntity.builder()
        .status(StatusInfo.CurrentStatusEnum.COMPLETED)
        .reason("completed successfully")
        .lastModifiedAt(System.currentTimeMillis())
        .build();
  }
}
