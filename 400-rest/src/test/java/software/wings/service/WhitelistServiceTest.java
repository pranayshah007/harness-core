/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.beans.security.access.GlobalWhitelistConfig;
import software.wings.beans.security.access.Whitelist;
import software.wings.beans.security.access.WhitelistStatus;
import software.wings.features.api.PremiumFeature;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.WhitelistService;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 *
 * @author rktummala
 */
public class WhitelistServiceTest extends WingsBaseTest {
  private static final String IP_ADDRESS_1 = "192.168.0.1";
  private static final String IP_ADDRESS_2 = "10.17.12.5";
  private static final String IP_ADDRESS_3 = "10.18.12.1";
  private static final String IP_ADDRESS_4 = "12.12.12.12";
  private static final String IP_ADDRESS_5 = "127.0.0.1";
  private static final String IP_ADDRESS_6 = "192.168.128.66";
  private static final String IP_ADDRESS_7 = "192.168.1.66";
  private static final String INVALID_IP_ADDRESS = "invalidIp";

  @Mock private AccountService accountService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private Account account;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private MainConfiguration mainConfig;

  @InjectMocks @Inject private WhitelistService whitelistService;
  @Mock private PremiumFeature ipWhitelistingFeature;

  private String accountId = UUIDGenerator.generateUuid();
  private String whitelistId = UUIDGenerator.generateUuid();
  private String description = "whitelist 1";
  private String cidrFilter = "10.17.12.1/24";
  private String cidrFilter1 = "10.18.12.1/32";
  private String invalidCidrFilter1 = "127.0.0.1//8";
  private String invalidCidrFilter2 = "127.0.0.1/8/b";
  private String ipFilter = IP_ADDRESS_1;
  private String invalidIpFilter1 = "999.0.0.1";
  private String invalidIpFilter2 = "127";
  private String invalidIpFilter3 = "abcd";

  /**
   * Sets mocks.
   */
  @Before
  public void setupMocks() {
    when(accountService.get(anyString())).thenReturn(account);
    when(mainConfig.getGlobalWhitelistConfig()).thenReturn(getHarnessDefaults());
    when(ipWhitelistingFeature.isAvailableForAccount(accountId)).thenReturn(true);

    setUserRequestContext();
  }

  private void setUserRequestContext() {
    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(UserRequestContext.builder().accountId(ACCOUNT_ID).build());
    UserThreadLocal.set(user);
  }

  private GlobalWhitelistConfig getHarnessDefaults() {
    return GlobalWhitelistConfig.builder().filters("192.168.128.0/24,127.0.0.1/8").build();
  }

  /**
   * Should save and read.
   *
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSaveAndRead() {
    Whitelist whitelist =
        Whitelist.builder().accountId(accountId).uuid(whitelistId).description(description).filter(cidrFilter).build();
    Whitelist savedWhitelist = whitelistService.save(whitelist);
    compare(whitelist, savedWhitelist);

    Whitelist whitelistFromGet = whitelistService.get(accountId, whitelistId);
    compare(savedWhitelist, whitelistFromGet);

    whitelist =
        Whitelist.builder().accountId(accountId).uuid(whitelistId).description(description).filter(ipFilter).build();
    savedWhitelist = whitelistService.save(whitelist);
    compare(whitelist, savedWhitelist);

    whitelistFromGet = whitelistService.get(accountId, whitelistId);
    compare(savedWhitelist, whitelistFromGet);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testList() {
    Whitelist whitelist1 = Whitelist.builder().accountId(accountId).description(description).filter(cidrFilter).build();
    Whitelist savedWhitelist1 = whitelistService.save(whitelist1);

    Whitelist whitelist2 = Whitelist.builder().accountId(accountId).description(description).filter(ipFilter).build();
    Whitelist savedWhitelist2 = whitelistService.save(whitelist2);

    PageResponse pageResponse = whitelistService.list(accountId, PageRequestBuilder.aPageRequest().build());
    assertThat(pageResponse).isNotNull();
    List<Whitelist> whitelistList = pageResponse.getResponse();
    assertThat(whitelistList).isNotNull();
    assertThat(whitelistList).hasSize(2);
    assertThat(whitelistList).containsExactlyInAnyOrder(savedWhitelist1, savedWhitelist2);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testIsValidIpAddress() {
    createWhitelists();

    boolean valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_1);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_2);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_3);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_4);
    assertThat(valid).isFalse();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_5);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_6);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_7);
    assertThat(valid).isFalse();

    valid = whitelistService.isValidIPAddress(accountId, INVALID_IP_ADDRESS);
    assertThat(valid).isFalse();
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testIsValidIpAddressTrueIfFeatureIsUnavailable() {
    createWhitelists();

    when(ipWhitelistingFeature.isAvailableForAccount(accountId)).thenReturn(false);

    boolean valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_1);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_2);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_3);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_4);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_5);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_6);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, IP_ADDRESS_7);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, INVALID_IP_ADDRESS);
    assertThat(valid).isTrue();
  }

  private void createWhitelists() {
    Whitelist whitelist1 = Whitelist.builder()
                               .accountId(accountId)
                               .description(description)
                               .filter(cidrFilter)
                               .status(WhitelistStatus.ACTIVE)
                               .build();
    whitelistService.save(whitelist1);

    Whitelist whitelist2 = Whitelist.builder()
                               .accountId(accountId)
                               .description(description)
                               .filter(ipFilter)
                               .status(WhitelistStatus.ACTIVE)
                               .build();
    whitelistService.save(whitelist2);

    Whitelist whitelist3 = Whitelist.builder()
                               .accountId(accountId)
                               .description(description)
                               .filter(cidrFilter1)
                               .status(WhitelistStatus.ACTIVE)
                               .build();
    whitelistService.save(whitelist3);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testInputValidation() {
    // Negative cases
    Whitelist whitelist = Whitelist.builder()
                              .accountId(accountId)
                              .uuid(whitelistId)
                              .description(description)
                              .filter(invalidCidrFilter1)
                              .build();
    try {
      whitelistService.save(whitelist);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelist = Whitelist.builder()
                    .accountId(accountId)
                    .uuid(whitelistId)
                    .description(description)
                    .filter(invalidCidrFilter2)
                    .build();
    try {
      whitelistService.save(whitelist);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelist =
        Whitelist.builder().accountId(accountId).uuid(whitelistId).description(description).filter(null).build();
    try {
      whitelistService.save(whitelist);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelist = Whitelist.builder()
                    .accountId(accountId)
                    .uuid(whitelistId)
                    .description(description)
                    .filter(invalidIpFilter1)
                    .build();
    try {
      whitelistService.save(whitelist);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelist = Whitelist.builder()
                    .accountId(accountId)
                    .uuid(whitelistId)
                    .description(description)
                    .filter(invalidIpFilter2)
                    .build();
    try {
      whitelistService.save(whitelist);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelist = Whitelist.builder()
                    .accountId(accountId)
                    .uuid(whitelistId)
                    .description(description)
                    .filter(invalidIpFilter3)
                    .build();
    try {
      whitelistService.save(whitelist);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testUpdateAndRead() {
    Whitelist whitelist =
        Whitelist.builder().accountId(accountId).uuid(whitelistId).description(description).filter(cidrFilter).build();
    Whitelist savedWhitelist = whitelistService.save(whitelist);
    compare(whitelist, savedWhitelist);

    Whitelist whitelistFromGet = whitelistService.get(accountId, whitelistId);
    compare(savedWhitelist, whitelistFromGet);

    whitelistFromGet.setFilter(ipFilter);

    Whitelist updatedWhitelist = whitelistService.update(whitelist);
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(eq(accountId), eq(null), any(Whitelist.class), eq(Type.CREATE));
    compare(whitelist, updatedWhitelist);

    whitelistFromGet = whitelistService.get(accountId, whitelistId);
    compare(updatedWhitelist, whitelistFromGet);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testUpdateWithInvalidInputs() {
    Whitelist whitelist =
        Whitelist.builder().accountId(accountId).uuid(whitelistId).description(description).filter(cidrFilter).build();
    whitelistService.save(whitelist);

    Whitelist whitelistFromGet = whitelistService.get(accountId, whitelistId);
    whitelistFromGet.setFilter(invalidCidrFilter1);
    try {
      whitelistService.update(whitelistFromGet);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelistFromGet.setFilter(invalidCidrFilter2);
    try {
      whitelistService.update(whitelistFromGet);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelistFromGet.setFilter(null);
    try {
      whitelistService.update(whitelistFromGet);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelistFromGet.setFilter(invalidIpFilter1);
    try {
      whitelistService.update(whitelistFromGet);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelistFromGet.setFilter(invalidIpFilter2);
    try {
      whitelistService.update(whitelistFromGet);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }

    whitelistFromGet.setFilter(invalidIpFilter3);
    try {
      whitelistService.update(whitelistFromGet);
      assertThat(true).isFalse();
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isNotNull();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testDelete() {
    Whitelist whitelist =
        Whitelist.builder().accountId(accountId).uuid(whitelistId).description(description).filter(cidrFilter).build();
    whitelistService.save(whitelist);
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(eq(accountId), eq(null), any(Whitelist.class), eq(Type.CREATE));

    boolean delete = whitelistService.delete(accountId, whitelistId);
    assertThat(delete).isTrue();

    Whitelist whitelistAfterDelete = whitelistService.get(accountId, whitelistId);
    assertThat(whitelistAfterDelete).isNull();
    whitelist.setStatus(WhitelistStatus.ACTIVE);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditingUsingAccountId(accountId, whitelist);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCheckIfFeatureIsEnabledAndWhitelisting() {
    createWhitelists();
    when(featureFlagService.isEnabled(FeatureName.WHITELIST_PUBLIC_API, accountId)).thenReturn(true);
    boolean valid = whitelistService.checkIfFeatureIsEnabledAndWhitelisting(
        accountId, IP_ADDRESS_1, FeatureName.WHITELIST_PUBLIC_API);
    assertThat(valid).isTrue();

    valid = whitelistService.checkIfFeatureIsEnabledAndWhitelisting(
        accountId, IP_ADDRESS_2, FeatureName.WHITELIST_PUBLIC_API);
    assertThat(valid).isTrue();

    valid = whitelistService.checkIfFeatureIsEnabledAndWhitelisting(
        accountId, IP_ADDRESS_7, FeatureName.WHITELIST_PUBLIC_API);
    assertThat(valid).isFalse();

    valid =
        whitelistService.checkIfFeatureIsEnabledAndWhitelisting(accountId, IP_ADDRESS_7, FeatureName.WHITELIST_GRAPHQL);
    assertThat(valid).isTrue();

    valid = whitelistService.isValidIPAddress(accountId, INVALID_IP_ADDRESS);
    assertThat(valid).isFalse();
  }

  private void compare(Whitelist lhs, Whitelist rhs) {
    assertThat(rhs.getUuid()).isEqualTo(lhs.getUuid());
    assertThat(rhs.getDescription()).isEqualTo(lhs.getDescription());
    assertThat(rhs.getFilter()).isEqualTo(lhs.getFilter());
    assertThat(rhs.getAccountId()).isEqualTo(lhs.getAccountId());
  }
}
