/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.beans.VaultConfig;
import software.wings.service.intfc.AlertService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import java.util.Optional;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RuntimeCredentialsInjectorTest extends WingsBaseTest {
  @Mock private AlertService alertService;
  @InjectMocks @Inject private VaultServiceImpl vaultService;

  @Inject private KryoSerializer kryoSerializer;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateRuntimeParameters_shouldSucceed() {
    SecretManagerConfig vaultConfig = VaultConfig.builder().build();
    VaultServiceImpl spyVaultService = spy(vaultService);
    Reflect.on(spyVaultService).set("kryoSerializer", kryoSerializer);
    vaultConfig.setTemplatizedFields(Lists.newArrayList("authToken"));

    doReturn("vaultId").when(spyVaultService).updateVaultConfig(any(), any(), anyBoolean(), anyBoolean());

    Optional<SecretManagerConfig> updatedVaultConfig = spyVaultService.updateRuntimeCredentials(
        vaultConfig, Maps.newHashMap(ImmutableMap.of("authToken", "abcde")), true);

    assertThat(updatedVaultConfig).isPresent();
    verify(spyVaultService).updateVaultConfig(any(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateRuntimeParameters_shouldFailDueToSecretManagerNotTemplatized() {
    SecretManagerConfig vaultConfig = VaultConfig.builder().build();
    VaultServiceImpl spyVaultService = spy(vaultService);
    Reflect.on(spyVaultService).set("kryoSerializer", kryoSerializer);

    Optional<SecretManagerConfig> updatedVaultConfig = spyVaultService.updateRuntimeCredentials(
        vaultConfig, Maps.newHashMap(ImmutableMap.of("authToken", "abcde")), true);
    assertThat(updatedVaultConfig).isNotPresent();
    verify(spyVaultService, times(0)).updateVaultConfig(any(), any(), anyBoolean(), anyBoolean());

    vaultConfig.setTemplatizedFields(Lists.newArrayList("authRole"));
    updatedVaultConfig = spyVaultService.updateRuntimeCredentials(vaultConfig, null, true);
    assertThat(updatedVaultConfig).isNotPresent();
    verify(spyVaultService, times(0)).updateVaultConfig(any(), any(), anyBoolean(), anyBoolean());

    updatedVaultConfig = spyVaultService.updateRuntimeCredentials(vaultConfig, Maps.newHashMap(), true);
    assertThat(updatedVaultConfig).isNotPresent();
    verify(spyVaultService, times(0)).updateVaultConfig(any(), any(), anyBoolean(), anyBoolean());

    vaultConfig.setTemplatizedFields(Lists.newArrayList("appRoleId", "secretId"));
    updatedVaultConfig = spyVaultService.updateRuntimeCredentials(
        vaultConfig, Maps.newHashMap(ImmutableMap.of("authToken", "abc")), true);
    assertThat(updatedVaultConfig).isNotPresent();
    verify(spyVaultService, times(0)).updateVaultConfig(any(), any(), anyBoolean(), anyBoolean());
  }
}
