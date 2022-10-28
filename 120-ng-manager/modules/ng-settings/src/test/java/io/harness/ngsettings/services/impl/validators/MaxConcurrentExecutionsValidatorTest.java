/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators;

import io.harness.CategoryTest;
import io.harness.MaxConcurrencyConfig;
import io.harness.MaxConcurrentExecutionsConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsettings.SettingGroupIdentifiers;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.harness.rule.OwnerRule.BRIJESH;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.doReturn;

public class MaxConcurrentExecutionsValidatorTest extends CategoryTest {
  @Mock private LicenseService licenseService;;
  @InjectMocks MaxConcurrentExecutionsValidator maxConcurrentExecutionsValidator;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testListWhenDefaultOnlyPresent() {
    String accountId = "accountId";
    MaxConcurrentExecutionsConfig maxConcurrentExecutionsConfig = MaxConcurrentExecutionsConfig.builder()
            .maxConcurrentNodesConfig(MaxConcurrencyConfig.builder().free(10).team(100).enterprise(1000).build())
            .maxConcurrentPipelinesConfig(MaxConcurrencyConfig.builder().free(5).team(50).enterprise(500).build())
            .build();
    SettingDTO oldSettingDTO = SettingDTO.builder()
            .identifier("setting_id")
            .value("5")
            .defaultValue("4")
            .groupIdentifier(SettingGroupIdentifiers.MAX_CONCURRENT_NODES)
            .build();
    SettingDTO newSettingDTO = SettingDTO.builder()
            .identifier("setting_id")
            .value("8")
            .groupIdentifier(SettingGroupIdentifiers.MAX_CONCURRENT_NODES)
            .build();

    on(maxConcurrentExecutionsValidator).set("maxConcurrentExecutionsConfig",maxConcurrentExecutionsConfig);

    // Checking the same validations for groupIdentifier MAX_CONCURRENT_NODES
    doReturn(Edition.FREE).when(licenseService).calculateAccountEdition(accountId);
    assertThatCode(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).doesNotThrowAnyException();
    // Increasing the value that is not permitted in Free plan. Will throw exception.
    newSettingDTO.setValue("12");
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

    // In team plan, above value will be permitted.
    doReturn(Edition.TEAM).when(licenseService).calculateAccountEdition(accountId);
    assertThatCode(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).doesNotThrowAnyException();
    // Increasing the value that is not permitted in Team plan. Will throw exception.
    newSettingDTO.setValue("120");
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

    // In Enterprise plan, above value will be permitted.
    doReturn(Edition.ENTERPRISE).when(licenseService).calculateAccountEdition(accountId);
    assertThatCode(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).doesNotThrowAnyException();
    // Increasing the value that is not permitted in Enterprise plan. Will throw exception.
    newSettingDTO.setValue("1200");
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

    // Checking the same validations for groupIdentifier MAX_CONCURRENT_PIPELINE
    doReturn(Edition.FREE).when(licenseService).calculateAccountEdition(accountId);
    oldSettingDTO.setGroupIdentifier(SettingGroupIdentifiers.MAX_CONCURRENT_PIPELINE);
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

    newSettingDTO.setValue("4");
    assertThatCode(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).doesNotThrowAnyException();
    newSettingDTO.setValue("40");
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

    doReturn(Edition.TEAM).when(licenseService).calculateAccountEdition(accountId);
    assertThatCode(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).doesNotThrowAnyException();
    newSettingDTO.setValue("400");
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

    doReturn(Edition.ENTERPRISE).when(licenseService).calculateAccountEdition(accountId);
    assertThatCode(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).doesNotThrowAnyException();
    newSettingDTO.setValue("4000");
    assertThatThrownBy(()->maxConcurrentExecutionsValidator.validate(accountId,oldSettingDTO,newSettingDTO)).isInstanceOf(InvalidRequestException.class);

  }
}
