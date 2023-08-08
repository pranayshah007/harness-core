/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.saml.SamlClientService;
import software.wings.security.saml.SamlUserGroupSync;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.util.StringInputStream;
import com.coveo.saml.SamlClient;
import com.coveo.saml.SamlException;
import com.coveo.saml.SamlResponse;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.GlobalParserPoolInitializer;
import org.opensaml.core.xml.config.XMLObjectProviderInitializer;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.config.impl.SAMLConfigurationInitializer;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;

@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SamlBasedAuthHandlerTest extends WingsBaseTest {
  @Mock AuthenticationUtils authenticationUtils;
  @Mock SSOSettingService ssoSettingService;
  @Mock SamlUserGroupSync samlUserGroupSync;
  @Mock SecretManager secretManager;
  @Mock EncryptionService encryptionService;
  @Mock NgSamlAuthorizationEventPublisher mockNgSamlAuthorizationPublisher;
  @Mock FeatureFlagService mockFeatureFlagService;
  @Mock UserService userService;
  @InjectMocks @Spy SamlClientService samlClientService;
  @Inject @InjectMocks private SamlBasedAuthHandler authHandler;

  private static final String oktaIdpUrl =
      "https://dev-274703.oktapreview.com/app/harnessiodev274703_testapp_1/exkefa5xlgHhrU1Mc0h7/sso/saml";

  private static final String googleIdpUrl1 =
      "https://accounts.google.com/o/saml2/initsso?idpid=C00pxqnjz&spid=256731830644&forceauthn=false&from_login=1&as=DzxFw6iKFGy_LYb42_bZ4g&pli=1&authuser=0";
  private static final String azureIdpUrl2 =
      "https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-730f6474e906/saml2?SAMLRequest=jZFfS8MwFMW%2FSsl7%2F2eahbbQrRsMpoxNffAtdHcu0CY1Nxn67c06HYIovh7O73DOvQWKvht47exRbeHVAdrgre8UlsQZxbVAiVyJHpDblu%2FquzXPooT3YMVeWEGCVVOSJaMNY6yu8xml83TGKM1vJzVdsCxNmumCBE9gUGpVEg97BtHBSqEVynopSVmYTMOUPmQ5T6Y8T6PsZvJ89m0EojxBSQ6iQyBBjQjG%2BqS5Vuh6MDswJ9nC43ZdkqO1A%2FI4bmV0FEYBYiR1LAYZO09hfJ4advpFKnLZyMfxfy8djLa61R2pirG2%2Bc95xFdNUn02KeILXRWXg997ZtVsdCfb92CpTS%2Fs75FplI6K3IeH0cqdwgFaeZCw98Xin5lX8ftnqw8%3D";

  @Before
  public void initMocks() throws IOException {
    String xml = IOUtils.toString(getClass().getResourceAsStream("/okta-IDP-metadata.xml"), Charset.defaultCharset());
    SamlSettings samlSettings = SamlSettings.builder()
                                    .metaDataFile(xml)
                                    .url(oktaIdpUrl)
                                    .accountId("TestAccountID")
                                    .displayName("Okta")
                                    .origin("dev-274703.oktapreview.com")
                                    .build();
    when(ssoSettingService.getSamlSettingsByOrigin("dev-274703.oktapreview.com")).thenReturn(samlSettings);
    when(ssoSettingService.getSamlSettingsByAccountId(anyString())).thenReturn(samlSettings);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testSamlBasedValidationAssertionFails() throws IOException {
    try {
      User user = new User();
      Account account = new Account();
      user.setAccounts(Arrays.asList(account));
      String samlResponse =
          IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
      account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
      when(authenticationUtils.getUser(anyString())).thenReturn(user);
      when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
      assertThat(authHandler.getAuthenticationMechanism())
          .isEqualTo(io.harness.ng.core.account.AuthenticationMechanism.SAML);
      authHandler.authenticate(oktaIdpUrl, samlResponse);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("User does not exist");
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testSamlBasedValidationValidAssertionForOkta() throws IOException, SamlException {
    User user = new User();
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = mock(SamlSettings.class);
    when(samlSettings.getAccountId()).thenReturn("AC1");
    List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    User returnedUser = authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid()).getUser();
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testSamlBasedValidationValidAssertionForGoogle() throws IOException, SamlException {
    User user = new User();

    Account account = new Account();
    account.setUuid("TestGoogleAuthAccount1");
    user.setAccounts(Arrays.asList(account));
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);

    String xml = IOUtils.toString(getClass().getResourceAsStream("/GoogleIDPMetadata.xml"), Charset.defaultCharset());

    SamlSettings googleSamlSettings1 = SamlSettings.builder()
                                           .metaDataFile(xml)
                                           .url("https://accounts.google.com/o/saml2/initsso?idpid=C00pxqnjz")
                                           .accountId("TestGoogleAuthAccount1")
                                           .displayName("Google 1")
                                           .origin("accounts.google.com")
                                           .build();

    SamlSettings googleSamlSettings2 = SamlSettings.builder()
                                           .metaDataFile(xml)
                                           .url("https://accounts.google.com/o/saml2/initsso?idpid=C00pxqnjAAA")
                                           .accountId("TestGoogleAuthAccount2")
                                           .displayName("Google 2")
                                           .origin("accounts.google.com")
                                           .build();

    googleSamlSettings1 = spy(googleSamlSettings1);
    googleSamlSettings2 = spy(googleSamlSettings2);

    when(ssoSettingService.getSamlSettingsIteratorByOrigin("accounts.google.com", "TestGoogleAuthAccount2"))
        .thenReturn(Arrays.asList(googleSamlSettings1, googleSamlSettings2).iterator());

    doReturn(samlClient).when(samlClientService).getSamlClient(any(SamlSettings.class));
    when(samlClient.decodeAndValidateSamlResponse(any())).thenReturn(samlResponse);

    User returnedUser = authHandler.authenticate(googleIdpUrl1, samlResponseString, "TestGoogleAuthAccount2").getUser();
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAzureSaml() throws IOException, SamlException {
    User user = new User();
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("TestAzureAccount1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);

    String xml = IOUtils.toString(getClass().getResourceAsStream("/Azure-1-metadata.xml"), Charset.defaultCharset());

    SamlSettings azureSetting1 =
        SamlSettings.builder()
            .metaDataFile(xml)
            .url("https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-730f6474e906/saml2")
            .accountId("TestAzureAccount1")
            .displayName("Azure 1")
            .origin("login.microsoftonline.com")
            .build();

    SamlSettings azureSetting2 = SamlSettings.builder()
                                     .metaDataFile(xml)
                                     .url("https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-fakedata/saml2")
                                     .accountId("TestAzureAccount2")
                                     .displayName("Azure 2")
                                     .origin("login.microsoftonline.com")
                                     .build();

    azureSetting1 = spy(azureSetting1);
    azureSetting2 = spy(azureSetting2);

    when(ssoSettingService.getSamlSettingsIteratorByOrigin("login.microsoftonline.com", null))
        .thenReturn(Arrays.asList(azureSetting1, azureSetting2).iterator());

    doReturn(samlClient).when(samlClientService).getSamlClient(any(SamlSettings.class));
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    User returnedUser = authHandler.authenticate(azureIdpUrl2, samlResponseString, null).getUser();
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testAzureNonDefaultAccountSaml() throws IOException, SamlException {
    String accountId1 = "kmpySmUISimoRrJL6NL73w";
    String accountId2 = "TestAzureAccount1";
    User user = new User();
    user.setDefaultAccountId(accountId2);
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account1 = new Account();
    account1.setUuid(accountId1);
    Account account2 = new Account();
    account2.setUuid(accountId2);
    user.setAccounts(Arrays.asList(account1, account2));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account1.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(authenticationUtils.getAccount(accountId1)).thenReturn(account1);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account2);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);

    String xml = IOUtils.toString(getClass().getResourceAsStream("/Azure-1-metadata.xml"), Charset.defaultCharset());

    SamlSettings azureSetting1 =
        SamlSettings.builder()
            .metaDataFile(xml)
            .url("https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-730f6474e906/saml2")
            .accountId("TestAzureAccount1")
            .displayName("Azure 1")
            .origin("login.microsoftonline.com")
            .build();

    SamlSettings azureSetting2 = SamlSettings.builder()
                                     .metaDataFile(xml)
                                     .url("https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-fakedata/saml2")
                                     .accountId("TestAzureAccount2")
                                     .displayName("Azure 2")
                                     .origin("login.microsoftonline.com")
                                     .build();

    azureSetting1 = spy(azureSetting1);
    azureSetting2 = spy(azureSetting2);

    when(ssoSettingService.getSamlSettingsIteratorByOrigin("login.microsoftonline.com", accountId1))
        .thenReturn(Arrays.asList(azureSetting1, azureSetting2).iterator());

    doReturn(samlClient).when(samlClientService).getSamlClient(any(SamlSettings.class));
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    User returnedUser = authHandler.authenticate(azureIdpUrl2, samlResponseString, accountId1).getUser();
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testSamlAuthenticationAndGroupExtractionForOktaShouldSucceed() throws IOException, SamlException {
    User user = new User();
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = mock(SamlSettings.class);
    when(samlSettings.getAccountId()).thenReturn("AC1");
    List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    doNothing().when(samlUserGroupSync).syncUserGroup(any(SamlUserAuthorization.class), any(), any());
    doReturn(false).when(samlSettings).isAuthorizationEnabled();
    User returnedUser = authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid()).getUser();
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testValidateUser() {
    User user = new User();
    user.setEmail("test@test.com");
    Account account1 = new Account();
    account1.setUuid("AC1");

    Account account2 = new Account();
    account2.setUuid("AC2");

    user.setAccounts(Arrays.asList(account1, account2));

    try {
      authHandler.validateUser(user, "AC3");
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(WingsException.class);
    }

    try {
      authHandler.validateUser(user, "AC2");
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      authHandler.validateUser(user, "AC1");
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  /**
   * Tests if ErrorCode.SAML_TEST_SUCCESS_MECHANISM_NOT_ENABLED is thrown when SAML is authenticated without enabling it
   * @throws IOException
   * @throws SamlException
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAuthenticationWithSamlNotEnabled() throws IOException, SamlException {
    User user = new User();
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(AuthenticationMechanism.OAUTH);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = mock(SamlSettings.class);
    when(samlSettings.getAccountId()).thenReturn("AC1");
    List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    doNothing().when(samlUserGroupSync).syncUserGroup(any(SamlUserAuthorization.class), any(), any());
    doReturn(false).when(samlSettings).isAuthorizationEnabled();
    try {
      authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid());
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.SAML_TEST_SUCCESS_MECHANISM_NOT_ENABLED);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  @Ignore(
      "The test fails when the azure app settings are updated to point to any other environment apart from localdev")
  public void
  testUserGroupsExtractionForAzureShouldSucceed()
      throws IOException, SamlException, XMLParserException, UnmarshallingException, InitializationException,
             IllegalArgumentException {
    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse-2.txt"), Charset.defaultCharset());
    final String accountId = "kmpySmUISimoRrJL6NL73w";
    final String groupMembershipAttribute = "harness/harness-group";
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("prateek.barapatre@harness.io");
    when(samlResponse.getAssertion()).thenReturn(this.getSamlAssertion(samlResponseString));
    SamlClient samlClient = mock(SamlClient.class);
    SamlSettings samlSettings =
        SamlSettings.builder()
            .metaDataFile(
                IOUtils.toString(getClass().getResourceAsStream("/Azure-2-metadata.xml"), Charset.defaultCharset()))
            .url("https://login.microsoftonline.com")
            .accountId(accountId)
            .displayName("AzureSamlTest")
            .origin("https://login.microsoftonline.com")
            .groupMembershipAttr(groupMembershipAttribute)
            .entityIdentifier("localdev.harness.io")
            .build();

    samlSettings.setClientId("7a3cffef-e03e-4689-8418-6ecb96cfdf73");
    samlSettings.setEncryptedClientSecret("rlY3tfZWSGefvnxQUouTpQ");

    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    samlSettings.setClientSecret("gk~7Q~VVCMRs_yZNZY~1xIRBgXFuf8Gt8QuhX".toCharArray());
    when(encryptionService.decrypt(samlSettings, encryptedDataDetails, false)).thenReturn(samlSettings);

    when(ssoSettingService.getSamlSettingsByAccountId(anyString())).thenReturn(samlSettings);
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);
    Assertion samlAssertionValue = this.getSamlAssertion(samlResponseString);

    doNothing().when(samlUserGroupSync).syncUserGroup(any(SamlUserAuthorization.class), anyString(), anyString());
    List<String> groups = authHandler.getUserGroupsForAzure(samlAssertionValue.getAttributeStatements(), samlSettings,
        groupMembershipAttribute, samlAssertionValue.getIssuer().getValue(), accountId);
    assertThat(groups.size()).isGreaterThan(150);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetMatchingSamlSettingFromResponseAndIssuer() throws IOException, SamlException {
    final String accountId = "test_account_id";
    final String displayName = "SamlSettingResponseTest";
    User user = new User();
    user.setDefaultAccountId(accountId);
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(authenticationUtils.getUser(anyString())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);

    SamlResponse samlResponse = mock(SamlResponse.class);
    Assertion mockAssertion = mock(Assertion.class);
    Issuer mockIssuer = mock(Issuer.class);
    when(samlResponse.getAssertion()).thenReturn(mockAssertion);
    when(mockAssertion.getIssuer()).thenReturn(mockIssuer);
    when(mockIssuer.getValue()).thenReturn("https://sts.windows.net/b229b2bb-5f33-4d22-bce0-730f6474e906/");

    SamlSettings samlSettings =
        SamlSettings.builder()
            .metaDataFile(
                IOUtils.toString(getClass().getResourceAsStream("/Azure-2-metadata.xml"), Charset.defaultCharset()))
            .url("https://login.microsoftonline.com")
            .accountId(accountId)
            .displayName(displayName)
            .origin("https://login.microsoftonline.com")
            .groupMembershipAttr("testGroupMembership")
            .build();

    SamlClient samlClient = mock(SamlClient.class);
    List<SamlSettings> samlSettingsList = List.of(samlSettings);
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(samlResponseString)).thenReturn(samlResponse);
    SamlSettings matchedSamlSetting =
        authHandler.getMatchingSamlSettingFromResponseAndIssuer(samlResponseString, samlSettingsList, true);
    assertThat(matchedSamlSetting).isNotNull();
    assertThat(matchedSamlSetting.getDisplayName()).isEqualTo(displayName);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testPopulateIdPUrlIfEmpty() throws IOException, SamlException, URISyntaxException {
    final String accountId = "test_account_id";
    final String displayName = "SamlSettingResponseTest";
    User user = new User();
    user.setDefaultAccountId(accountId);
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));
    String idpUrl = null;

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(authenticationUtils.getUser(anyString())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(any(), any())).thenReturn(false);

    SamlResponse samlResponse = mock(SamlResponse.class);
    Assertion mockAssertion = mock(Assertion.class);
    Issuer mockIssuer = mock(Issuer.class);
    when(samlResponse.getAssertion()).thenReturn(mockAssertion);
    when(mockAssertion.getIssuer()).thenReturn(mockIssuer);
    when(mockIssuer.getValue()).thenReturn("https://sts.windows.net/b229b2bb-5f33-4d22-bce0-730f6474e906/");

    SamlSettings samlSettings =
        SamlSettings.builder()
            .metaDataFile(
                IOUtils.toString(getClass().getResourceAsStream("/Azure-2-metadata.xml"), Charset.defaultCharset()))
            .url("https://login.microsoftonline.com")
            .accountId(accountId)
            .displayName(displayName)
            .origin("https://login.microsoftonline.com")
            .groupMembershipAttr("testGroupMembership")
            .build();

    SamlClient samlClient = mock(SamlClient.class);
    List<SamlSettings> samlSettingsList = List.of(samlSettings);
    doReturn(samlSettingsList).when(ssoSettingService).getSamlSettingsListByAccountId(anyString());
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(samlResponseString)).thenReturn(samlResponse);
    idpUrl = authHandler.populateIdPUrlIfEmpty(idpUrl, samlResponseString, accountId);
    assertThat(idpUrl).isNotNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testProcessNGSamlGroupSyncForNotSignedInSamlSettings() throws IOException {
    final String accountId = "test_account_id";
    final String displayName = "SamlSettingResponseTest";
    final String uuid = "testUuidString";
    SamlSettings samlSettings =
        SamlSettings.builder()
            .metaDataFile(
                IOUtils.toString(getClass().getResourceAsStream("/Azure-2-metadata.xml"), Charset.defaultCharset()))
            .url("https://login.microsoftonline.com")
            .accountId(accountId)
            .displayName(displayName)
            .origin("https://login.microsoftonline.com")
            .groupMembershipAttr("testGroupMembership")
            .build();
    samlSettings.setUuid(uuid);
    List<SamlSettings> samlSettingsList = List.of(samlSettings);
    authHandler.processNGSamlGroupSyncForNotSignedInSamlSettings(
        samlSettingsList, samlSettings, "test_user@mailinator.in", accountId);
    verify(mockNgSamlAuthorizationPublisher, times(0))
        .publishSamlAuthorizationAssertion(any(), anyString(), anyString());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSamlAuthentication_withIdpUrlAsNULL() throws IOException, SamlException, URISyntaxException {
    User user = new User();
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(any(), any())).thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("rushabh@harness.io");
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = mock(SamlSettings.class);
    when(samlSettings.getAccountId()).thenReturn("AC1");
    List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    Iterator<SamlSettings> samlSettingsIterator = samlSettingsList.iterator();
    doReturn(samlSettingsIterator).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    User returnedUser = authHandler.authenticate(null, samlResponseString, account.getUuid()).getUser();
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testNewUserWithJitProvisionedEnabled_shouldCreateUserAndLogin() throws IOException, SamlException {
    User user = new User();
    String email = "newJitUser@gmail.com";
    user.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    user.setUuid("kmpySmUISimoRrJL6NL73w");
    user.setEmail(email);
    Account account = new Account();
    account.setUuid("AC1");
    user.setAccounts(Arrays.asList(account));

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(null);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn(email);
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = mock(SamlSettings.class);
    when(samlSettings.getAccountId()).thenReturn("AC1");
    when(samlSettings.isJitEnabled()).thenReturn(true);
    List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    when(userService.completeUserCreationOrAdditionViaJitAndSignIn(argumentCaptor.capture(), any())).thenReturn(user);

    User returnedUser = authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid()).getUser();
    assertThat(argumentCaptor.getValue()).isEqualTo(email);
    assertThat(returnedUser).isEqualTo(user);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testNewUserWithJitProvisionedDisabled_shouldNotCreateUser() throws IOException, SamlException {
    String email = "newJitUser@gmail.com";
    Account account = new Account();
    account.setUuid("AC1");

    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(null);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn(email);
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = mock(SamlSettings.class);
    when(samlSettings.getAccountId()).thenReturn("AC1");
    when(samlSettings.isJitEnabled()).thenReturn(false);
    List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    doReturn(samlSettingsList.iterator()).when(samlClientService).getSamlSettingsFromOrigin(any(), any());
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);

    try {
      User returnedUser = authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid()).getUser();
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
      assertThat(ex.getMessage()).isEqualTo("User does not exist");
    }
    verify(userService, times(0)).completeUserCreationOrAdditionViaJitAndSignIn(any(), any());
  }
  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testSamlAuthenticationWithSameExternalIdHavingEntriesWithDifferentEmails()
      throws IOException, SamlException, URISyntaxException, XMLParserException, InitializationException,
             UnmarshallingException {
    User userByEmail = new User();
    userByEmail.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    userByEmail.setUuid("kmpySmUISimoRrJL6NL73w");
    userByEmail.setEmail("abc@harness.io");
    Account account = new Account();
    account.setUuid("AC1");
    userByEmail.setAccounts(Arrays.asList(account));
    User userByExternalID = new User();
    userByExternalID.setUuid("kmpySmUISimoRrJL6NL73wd");
    userByExternalID.setExternalUserId("external123");
    userByExternalID.setEmail("123@harness.io");
    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByEmail(any(), any())).thenReturn(userByEmail);
    when(userService.getUserByUserId(any(), any())).thenReturn(userByExternalID);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    when(mockFeatureFlagService.isEnabled(FeatureName.EXTERNAL_USERID_BASED_LOGIN, account.getUuid())).thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("abc@harness.io");
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = SamlSettings.builder().accountId("AC1").jitEnabled(false).build();
    final List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    when(samlClientService.getSamlSettingsFromOrigin(any(), any())).thenAnswer(new Answer<Iterator<SamlSettings>>() {
      @Override
      public Iterator<SamlSettings> answer(final InvocationOnMock invocation) throws Throwable {
        return samlSettingsList.iterator();
      }
    });

    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);
    when(samlResponse.getAssertion()).thenReturn(this.getSamlAssertion(samlResponseString));
    doNothing().when(samlUserGroupSync).syncUserGroup(any(SamlUserAuthorization.class), any(), any());
    User returnedUser = authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid()).getUser();
    assertThat(returnedUser).isEqualTo(userByEmail);
  }
  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testSamlAuthenticationWithExternalIdAndNoUserWithGivenEmail()
      throws IOException, SamlException, URISyntaxException, XMLParserException, InitializationException,
             UnmarshallingException {
    Account account = new Account();
    account.setUuid("AC1");
    User userByExternalID = new User();
    userByExternalID.setUuid("kmpySmUISimoRrJL6NL73wd");
    userByExternalID.setExternalUserId("external123");
    userByExternalID.setEmail("123@harness.io");
    User userExpected = new User();
    userExpected.setExternalUserId("external123");
    userExpected.setEmail("abc@harness.io");
    userExpected.setUuid("kmpySmUISimoRrJL6NL73wd");
    userExpected.setAccounts(Arrays.asList(account));
    String samlResponseString =
        IOUtils.toString(getClass().getResourceAsStream("/SamlResponse.txt"), Charset.defaultCharset());
    account.setAuthenticationMechanism(io.harness.ng.core.account.AuthenticationMechanism.SAML);
    when(userService.getUserByUserId(any(), any())).thenReturn(userByExternalID);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(mockFeatureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()))
        .thenReturn(true);
    when(mockFeatureFlagService.isEnabled(FeatureName.EXTERNAL_USERID_BASED_LOGIN, account.getUuid())).thenReturn(true);
    SamlResponse samlResponse = mock(SamlResponse.class);
    when(samlResponse.getNameID()).thenReturn("abc@harness.io");
    SamlClient samlClient = mock(SamlClient.class);
    final SamlSettings samlSettings = SamlSettings.builder().accountId("AC1").jitEnabled(false).build();
    final List<SamlSettings> samlSettingsList = Arrays.asList(samlSettings);
    when(samlClientService.getSamlSettingsFromOrigin(any(), any())).thenAnswer(new Answer<Iterator<SamlSettings>>() {
      @Override
      public Iterator<SamlSettings> answer(final InvocationOnMock invocation) throws Throwable {
        return samlSettingsList.iterator();
      }
    });
    when(ssoSettingService.getSamlSettingsByAccountId(anyString())).thenReturn(samlSettings);
    doReturn(samlClient).when(samlClientService).getSamlClient(samlSettings);
    when(samlClient.decodeAndValidateSamlResponse(anyString())).thenReturn(samlResponse);
    when(samlResponse.getAssertion()).thenReturn(this.getSamlAssertion(samlResponseString));
    doNothing().when(samlUserGroupSync).syncUserGroup(any(SamlUserAuthorization.class), any(), any());
    User returnedUser = authHandler.authenticate(oktaIdpUrl, samlResponseString, account.getUuid()).getUser();
    assertThat(returnedUser).isEqualTo(userExpected);
  }
  private Assertion getSamlAssertion(String samlResponse) throws IOException, XMLParserException, UnmarshallingException
                                                                 ,
                                                                 InitializationException, IllegalArgumentException {
    InitializationService.initialize();
    SAMLConfigurationInitializer samlInitializer = new SAMLConfigurationInitializer();
    samlInitializer.init();

    XMLObjectProviderInitializer xmlProviderInitializer = new XMLObjectProviderInitializer();
    xmlProviderInitializer.init();

    GlobalParserPoolInitializer parserPoolInitializer = new GlobalParserPoolInitializer();
    parserPoolInitializer.init();
    byte[] decode = new Base64().decode(samlResponse);
    String decodedSAMLStr = new String(decode, StandardCharsets.UTF_8);
    Response response = (Response) XMLObjectSupport.unmarshallFromInputStream(
        XMLObjectProviderRegistrySupport.getParserPool(), new StringInputStream(decodedSAMLStr));
    return response.getAssertions().get(0);
  }
}
