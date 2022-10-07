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

import software.wings.beans.container.HelmChartSpecification;

import com.esotericsoftware.kryo.Kryo;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HelmChartSpecificationKryoDtoTest extends CategoryTest {
  private static final int REGISTRATION_ID = 421;

  private static final KryoSerializer originalSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(OriginalRegistrar.class)), true);
  private static final KryoSerializer dtoSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(DtoRegistrar.class)), true);

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromOriginalToDto() {
    HelmChartSpecification helmChartSpecification =
        HelmChartSpecification.builder().chartUrl("someUrl").chartName("someName").chartVersion("someVersion").build();

    // serialize and deserialize to dto
    software.wings.beans.dto.HelmChartSpecification helmChartSpecificationDto =
        (software.wings.beans.dto.HelmChartSpecification) dtoSerializer.asObject(
            originalSerializer.asBytes(helmChartSpecification));

    assertThat(helmChartSpecificationDto.getChartUrl()).isEqualTo(helmChartSpecification.getChartUrl());
    assertThat(helmChartSpecificationDto.getChartName()).isEqualTo(helmChartSpecification.getChartName());
    assertThat(helmChartSpecificationDto.getChartVersion()).isEqualTo(helmChartSpecification.getChartVersion());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromDtoToOriginal() {
    software.wings.beans.dto.HelmChartSpecification helmChartSpecificationDto =
        software.wings.beans.dto.HelmChartSpecification.builder()
            .chartUrl("someUrl")
            .chartName("someName")
            .chartVersion("someVersion")
            .build();

    // serialize and deserialize to dto
    HelmChartSpecification helmChartSpecification =
        (HelmChartSpecification) originalSerializer.asObject(dtoSerializer.asBytes(helmChartSpecificationDto));

    assertThat(helmChartSpecification.getChartUrl()).isEqualTo(helmChartSpecificationDto.getChartUrl());
    assertThat(helmChartSpecification.getChartName()).isEqualTo(helmChartSpecificationDto.getChartName());
    assertThat(helmChartSpecification.getChartVersion()).isEqualTo(helmChartSpecificationDto.getChartVersion());
  }

  public static class OriginalRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(HelmChartSpecification.class, REGISTRATION_ID);
    }
  }

  public static class DtoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(software.wings.beans.dto.HelmChartSpecification.class, REGISTRATION_ID);
    }
  }
}
