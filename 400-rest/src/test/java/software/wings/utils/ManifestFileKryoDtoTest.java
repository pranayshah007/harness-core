/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.appmanifest.ManifestFile;

import com.esotericsoftware.kryo.Kryo;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ManifestFileKryoDtoTest extends CategoryTest {
  private static final int REGISTRATION_ID = 421;

  private static final KryoSerializer originalSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(OriginalRegistrar.class)), true);
  private static final KryoSerializer dtoSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(DtoRegistrar.class)), true);

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromOriginalToDto() {
    ManifestFile manifestFile = ManifestFile.builder()
                                    .fileName("someFileName1")
                                    .fileContent("someContent1")
                                    .applicationManifestId("someManifestId1")
                                    .accountId("someAccountId1")
                                    .build();

    // serialize and deserialize to dto
    software.wings.beans.dto.ManifestFile manifestFileDto =
        (software.wings.beans.dto.ManifestFile) dtoSerializer.asObject(originalSerializer.asBytes(manifestFile));

    assertThat(manifestFileDto.getFileName()).isEqualTo(manifestFile.getFileName());
    assertThat(manifestFileDto.getFileContent()).isEqualTo(manifestFile.getFileContent());
    assertThat(manifestFileDto.getApplicationManifestId()).isEqualTo(manifestFile.getApplicationManifestId());
    assertThat(manifestFileDto.getAccountId()).isEqualTo(manifestFile.getAccountId());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromDtoToOriginal() {
    software.wings.beans.dto.ManifestFile manifestFileDto = software.wings.beans.dto.ManifestFile.builder()
                                                                .fileName("someFileName1")
                                                                .fileContent("someContent1")
                                                                .applicationManifestId("someManifestId1")
                                                                .accountId("someAccountId1")
                                                                .build();

    // serialize and deserialize to dto
    ManifestFile manifestFile = (ManifestFile) originalSerializer.asObject(dtoSerializer.asBytes(manifestFileDto));

    assertThat(manifestFile.getFileName()).isEqualTo(manifestFileDto.getFileName());
    assertThat(manifestFile.getFileContent()).isEqualTo(manifestFileDto.getFileContent());
    assertThat(manifestFile.getApplicationManifestId()).isEqualTo(manifestFileDto.getApplicationManifestId());
    assertThat(manifestFile.getAccountId()).isEqualTo(manifestFileDto.getAccountId());
  }

  public static class OriginalRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(ManifestFile.class, REGISTRATION_ID);
    }
  }

  public static class DtoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(software.wings.beans.dto.ManifestFile.class, REGISTRATION_ID);
    }
  }
}
