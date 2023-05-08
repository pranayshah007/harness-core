/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.saml;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRATEEK;

import static software.wings.beans.Account.Builder.anAccount;

import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.SSORequest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.sso.SamlSettings;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.intfc.SSOSettingService;
import software.wings.utils.WingsTestConstants;

import com.coveo.saml.SamlClient;
import com.coveo.saml.SamlException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SamlClientServiceTest extends CategoryTest {
  @Mock private AccountServiceImpl accountService;
  @Mock SSOSettingService ssoSettingService;

  @InjectMocks @Spy private SamlClientService samlClientService;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetSamlClientFromAccountAndSamlId() throws IOException, SamlException {
    String uuidString = UUID.randomUUID().toString();

    Account testAccount = anAccount()
                              .withUuid("testAccountId")
                              .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                              .withCompanyName(WingsTestConstants.COMPANY_NAME)
                              .build();

    when(ssoSettingService.getSamlSettingsByAccountIdAndUuid(anyString(), anyString()))
        .thenReturn(getSamlSettingsList(uuidString).get(0));
    SamlClient samlClient = samlClientService.getSamlClientFromAccountAndSamlId(testAccount, uuidString);
    assertNotNull(samlClient);
    assertNotNull(samlClient.getSamlRequest());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGenerateTestSamlRequest() throws IOException {
    final String uuidStr = UUID.randomUUID().toString();
    String testAccountId = "testAccountId";

    Account testAccount = anAccount()
                              .withUuid(testAccountId)
                              .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                              .withCompanyName(WingsTestConstants.COMPANY_NAME)
                              .build();

    when(ssoSettingService.getSamlSettingsByAccountId(testAccountId)).thenReturn(getSamlSettingsList(uuidStr).get(0));
    when(accountService.get(testAccountId)).thenReturn(testAccount);
    SSORequest ssoRequestResult = samlClientService.generateTestSamlRequest(testAccountId);
    assertNotNull(ssoRequestResult);
    assertNotNull(ssoRequestResult.getIdpRedirectUrl());
  }

  private List<SamlSettings> getSamlSettingsList(String uuidString) throws IOException {
    String xml = IOUtils.toString(getClass().getResourceAsStream("/Azure-1-metadata.xml"), Charset.defaultCharset());

    SamlSettings azureSetting1 =
        SamlSettings.builder()
            .metaDataFile(xml)
            .url("https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-730f6474e906/saml2")
            .accountId("TestAzureAccount1")
            .displayName("Azure001")
            .origin("login.microsoftonline.com")
            .build();
    azureSetting1.setUuid(uuidString);
    List<SamlSettings> samlSettingsList = new ArrayList<>();
    samlSettingsList.add(azureSetting1);
    return samlSettingsList;
  }
}
