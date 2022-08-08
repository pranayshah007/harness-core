/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializer;

import com.esotericsoftware.kryo.Kryo;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LdapSettingsKryoDtoTest extends CategoryTest {
  private static final int REGISTRATION_ID = 421;

  private static final KryoSerializer originalSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(OriginalRegistrar.class)), true);
  private static final KryoSerializer dtoSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(DtoRegistrar.class)), true);

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromOriginalToDto() {
    LdapConnectionSettings settings = new LdapConnectionSettings();
    settings.setBindPassword("somePassword");
    LdapSettings ldapSettings = LdapSettings.builder()
                                    .connectionSettings(settings)
                                    .displayName("someDisplayName")
                                    .accountId("someAccountId")
                                    .build();
    ldapSettings.setUuid("someUuid");

    // serialize and deserialize to dto
    software.wings.beans.dto.LdapSettings ldapSettingsDTO =
        (software.wings.beans.dto.LdapSettings) dtoSerializer.asObject(originalSerializer.asBytes(ldapSettings));

    assertThat(ldapSettingsDTO.getDisplayName()).isEqualTo(ldapSettings.getDisplayName());
    assertThat(ldapSettingsDTO.getAccountId()).isEqualTo(ldapSettings.getAccountId());
    assertThat(ldapSettingsDTO.getUuid()).isEqualTo(ldapSettings.getUuid());
    assertThat(ldapSettingsDTO.getConnectionSettings().getBindPassword())
        .isEqualTo(ldapSettings.getConnectionSettings().getBindPassword());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromDtoToOriginal() {
    LdapConnectionSettings settings = new LdapConnectionSettings();
    settings.setBindPassword("somePassword");

    software.wings.beans.dto.LdapSettings ldapSettingsDTO = software.wings.beans.dto.LdapSettings.builder()
                                                                .uuid("someUuid")
                                                                .accountId("someAccountId")
                                                                .displayName("someDisplayName")
                                                                .connectionSettings(settings)
                                                                .build();

    // serialize and deserialize to dto
    LdapSettings ldapSettings = (LdapSettings) originalSerializer.asObject(dtoSerializer.asBytes(ldapSettingsDTO));

    assertThat(ldapSettings.getDisplayName()).isEqualTo(ldapSettingsDTO.getDisplayName());
    assertThat(ldapSettings.getAccountId()).isEqualTo(ldapSettingsDTO.getAccountId());
    assertThat(ldapSettings.getUuid()).isEqualTo(ldapSettingsDTO.getUuid());
    assertThat(ldapSettings.getConnectionSettings().getBindPassword())
        .isEqualTo(ldapSettingsDTO.getConnectionSettings().getBindPassword());
  }

  public static void registerCommons(Kryo kryo) {
    // These IDs are not related to prod IDs.
    int id = 10000;
    kryo.register(LdapConnectionSettings.class, id++);
    kryo.register(SSOType.class, id++);
  }

  public static class OriginalRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      registerCommons(kryo);
      kryo.register(LdapSettings.class, REGISTRATION_ID);
    }
  }

  public static class DtoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      registerCommons(kryo);
      kryo.register(software.wings.beans.dto.LdapSettings.class, REGISTRATION_ID);
    }
  }
}
