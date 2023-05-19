/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.communication;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.communication.entities.CECommunications;
import io.harness.ccm.communication.entities.CommunicationType;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._490_CE_COMMONS)
@OwnedBy(CE)
public class CECommunicationsServiceImplTest extends WingsBaseTest {
  @Mock private CECommunicationsDao communicationsDao;
  @Mock CeAccountExpirationChecker accountChecker;
  @Inject @InjectMocks private CECommunicationsServiceImpl communicationsService;

  private String accountId = "ACCOUNT_ID";
  private String accountId2 = "ACCOUNT_ID2";
  private String uuid = "uuid";
  private CECommunications communications;
  private CECommunications newCommunications;
  private String email = "user@harness.io";
  private String defaultEmail = "default@harness.io";
  private CommunicationType type = CommunicationType.WEEKLY_REPORT;
  private boolean enable = true;

  @Before
  public void setUp() {
    communications =
        CECommunications.builder().accountId(accountId).uuid(uuid).emailId(email).type(type).enabled(enable).build();
    newCommunications = CECommunications.builder()
                            .accountId(accountId2)
                            .emailId(defaultEmail)
                            .type(type)
                            .enabled(enable)
                            .selfEnabled(true)
                            .build();
    when(communicationsDao.get(accountId, email, type)).thenReturn(communications);
    when(communicationsDao.get(accountId2, defaultEmail, type)).thenReturn(null);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGet() throws Exception {
    CECommunications result = communicationsService.get(accountId, email, type);
    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(uuid);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdate() throws Exception {
    communicationsService.update(accountId, email, type, false, true);
    verify(communicationsDao).update(eq(accountId), eq(email), eq(type), eq(false));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdateAsSave() throws Exception {
    communicationsService.update(accountId2, defaultEmail, type, true, true);
    verify(communicationsDao).save(eq(newCommunications));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDelete() throws Exception {
    communicationsService.delete(accountId, email, type);
    verify(communicationsDao).delete(eq(uuid));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetEntriesEnabledViaEmail() {
    List<CECommunications> enabledEntries = communicationsService.getEntriesEnabledViaEmail(accountId);
    verify(communicationsDao).getEntriesEnabledViaEmail(eq(accountId));
  }
}
