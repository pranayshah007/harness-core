/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.checks;

import static io.harness.licensing.EditionAction.CONTACT_SALES;
import static io.harness.licensing.EditionAction.EXTEND_TRIAL;
import static io.harness.licensing.EditionAction.MANAGE;
import static io.harness.licensing.EditionAction.START_FREE;
import static io.harness.licensing.EditionAction.START_TRIAL;
import static io.harness.licensing.EditionAction.UPGRADE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.licensing.Edition;
import io.harness.licensing.EditionAction;
import io.harness.licensing.LicenseType;
import io.harness.licensing.checks.impl.DefaultLicenseComplianceResolver;
import io.harness.licensing.checks.impl.EnterpriseChecker;
import io.harness.licensing.checks.impl.FreeChecker;
import io.harness.licensing.checks.impl.TeamChecker;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DefaultLicenseComplianceResolverTest extends CategoryTest {
  private DefaultLicenseComplianceResolver defaultLicenseComplianceResolver;
  private ModuleLicenseRepository licenseRepository;
  private Map<Edition, LicenseEditionChecker> licenseEditionCheckerMap;

  private static final String ACCOUNT_ID = "account";
  private static final ModuleType MODULE_TYPE = ModuleType.CD;
  private static final Set<EditionAction> EMPTY_SET = new HashSet<>();

  @Before
  public void setup() {
    licenseRepository = mock(ModuleLicenseRepository.class);
    licenseEditionCheckerMap = ImmutableMap.<Edition, LicenseEditionChecker>builder()
                                   .put(Edition.FREE, new FreeChecker())
                                   .put(Edition.TEAM, new TeamChecker())
                                   .put(Edition.ENTERPRISE, new EnterpriseChecker())
                                   .build();
    defaultLicenseComplianceResolver =
        new DefaultLicenseComplianceResolver(licenseRepository, licenseEditionCheckerMap);
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testNoLicense() {
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE)).thenReturn(new ArrayList<>());
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(Sets.newHashSet(START_FREE));
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(START_TRIAL, CONTACT_SALES));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(START_TRIAL, CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testActiveFree() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Long.MAX_VALUE);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.FREE);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(UPGRADE, CONTACT_SALES));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(UPGRADE, CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testActiveTeamTrial() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Long.MAX_VALUE);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.TEAM);
    license.setLicenseType(LicenseType.TRIAL);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testExpiredTeamTrialCanExtend() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.TEAM);
    license.setLicenseType(LicenseType.TRIAL);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(EXTEND_TRIAL, CONTACT_SALES));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testExpiredTeamTrial() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(0);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.TEAM);
    license.setLicenseType(LicenseType.TRIAL);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testActiveTeamPaid() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Long.MAX_VALUE);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.TEAM);
    license.setLicenseType(LicenseType.PAID);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(MANAGE));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(UPGRADE, CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void expiredTeamPaid() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(0);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.TEAM);
    license.setLicenseType(LicenseType.PAID);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(Sets.newHashSet(MANAGE));
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(UPGRADE, CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testActiveEnterpriseTrial() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Long.MAX_VALUE);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.ENTERPRISE);
    license.setLicenseType(LicenseType.TRIAL);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testExpiredeEnterpriseTrialCanExtend() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.ENTERPRISE);
    license.setLicenseType(LicenseType.TRIAL);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(EXTEND_TRIAL, CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testExpiredeEnterpriseTrial() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(0);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.ENTERPRISE);
    license.setLicenseType(LicenseType.TRIAL);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(CONTACT_SALES));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testActiveEnterprisePaid() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(Long.MAX_VALUE);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.ENTERPRISE);
    license.setLicenseType(LicenseType.PAID);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(MANAGE));
  }

  @Test
  @Owner(developers = OwnerRule.ZHUO)
  @Category(UnitTests.class)
  public void testExpiredEnterprisePaid() {
    CDModuleLicense license = CDModuleLicense.builder().build();
    license.setExpiryTime(0);
    license.setModuleType(ModuleType.CD);
    license.setEdition(Edition.ENTERPRISE);
    license.setLicenseType(LicenseType.PAID);
    when(licenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, MODULE_TYPE))
        .thenReturn(Lists.newArrayList(license));
    when(licenseRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(new ArrayList<>());
    Map<Edition, Set<EditionAction>> editionStates =
        defaultLicenseComplianceResolver.getEditionStates(MODULE_TYPE, ACCOUNT_ID);

    assertThat(editionStates.get(Edition.FREE)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.TEAM)).isEqualTo(EMPTY_SET);
    assertThat(editionStates.get(Edition.ENTERPRISE)).isEqualTo(Sets.newHashSet(MANAGE));
  }
}
