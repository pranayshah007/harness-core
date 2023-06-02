/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.rule.Owner;
import io.harness.workers.background.iterator.SettingAttributeValidateConnectivityHandler;

import software.wings.WingsBaseTest;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.SettingValidationService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SettingAttributeValidateConnectivityHandlerTest extends WingsBaseTest {
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private SettingsService settingsService;
  @Mock private SettingValidationService settingValidationService;
  @InjectMocks @Inject private SettingAttributeValidateConnectivityHandler settingAttributeValidateConnectivityHandler;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldRegisterIterators() {
    settingAttributeValidateConnectivityHandler.createAndStartIterator(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("SettingAttributeValidateConnectivity")
            .poolSize(5)
            .interval(Duration.ofMinutes(10))
            .build(),
        Duration.ofHours(3));
    verify(persistenceIteratorFactory)
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(SettingAttributeValidateConnectivityHandler.class), any());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleNewError() {
    String errMsg = "e1";
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build();
    when(settingValidationService.validate(settingAttribute)).thenThrow(new InvalidArtifactServerException(errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    assertThat(settingAttribute.getConnectivityError()).isEqualTo(errMsg);
    verify(settingsService).update(eq(settingAttribute), eq(false));
    resetMocks();

    errMsg = "e2";
    settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build();
    when(settingValidationService.validate(settingAttribute))
        .thenThrow(new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER).addParam("message", errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    assertThat(settingAttribute.getConnectivityError()).isEqualTo(errMsg);
    verify(settingsService).update(eq(settingAttribute), eq(false));
    resetMocks();

    errMsg = "e3";
    settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build();
    when(settingValidationService.validate(settingAttribute))
        .thenThrow(new WingsException(ErrorCode.INVALID_CLOUD_PROVIDER).addParam("message", errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    assertThat(settingAttribute.getConnectivityError()).isEqualTo(errMsg);
    verify(settingsService).update(eq(settingAttribute), eq(false));
    resetMocks();

    String oldErrMsg = "eold";
    errMsg = "e4";
    settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withConnectivityError(oldErrMsg).build();
    when(settingValidationService.validate(settingAttribute)).thenThrow(new InvalidArtifactServerException(errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    assertThat(settingAttribute.getConnectivityError()).isEqualTo(errMsg);
    verify(settingsService).update(eq(settingAttribute), eq(false));
    resetMocks();

    settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withConnectivityError(oldErrMsg).build();
    when(settingValidationService.validate(settingAttribute)).thenReturn(false);
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    assertThat(settingAttribute.getConnectivityError()).isNotEmpty();
    verify(settingsService).update(eq(settingAttribute), eq(false));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleSameError() {
    String errMsg = "e1";
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withConnectivityError(errMsg).build();
    when(settingValidationService.validate(settingAttribute)).thenThrow(new InvalidArtifactServerException(errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    verifyNoInteractions(settingsService);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleInvalidRequestExceptions() {
    String errMsg = "e1";
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build();
    when(settingValidationService.validate(settingAttribute)).thenThrow(new InvalidRequestException(errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    verifyNoInteractions(settingsService);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleNonWingsException() {
    String errMsg = "e1";
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build();
    when(settingValidationService.validate(settingAttribute)).thenThrow(new RuntimeException(errMsg));
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    verifyNoInteractions(settingsService);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleSameNullError() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build();
    when(settingValidationService.validate(settingAttribute)).thenReturn(true);
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    verifyNoInteractions(settingsService);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleRemovalOfError() {
    String errMsg = "e1";
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withConnectivityError(errMsg).build();
    when(settingValidationService.validate(settingAttribute)).thenReturn(true);
    settingAttributeValidateConnectivityHandler.handle(settingAttribute);
    verify(settingsService).update(eq(settingAttribute), eq(true));
  }

  private void resetMocks() {
    Mockito.reset(settingValidationService);
    Mockito.reset(settingsService);
    Mockito.reset(persistenceIteratorFactory);
  }
}
