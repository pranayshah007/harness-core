/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

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

public class EcsServiceSpecificationKryoDtoTest extends CategoryTest {
  private static final int REGISTRATION_ID = 421;

  private static final KryoSerializer originalSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(OriginalRegistrar.class)), true);
  private static final KryoSerializer dtoSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(DtoRegistrar.class)), true);

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromOriginalToDto() {
    EcsServiceSpecification ecsServiceSpecification = EcsServiceSpecification.builder()
                                                          .serviceId("someServiceId")
                                                          .serviceSpecJson("{ \"isThisJson\": true }")
                                                          .build();

    // serialize and deserialize to dto
    software.wings.beans.dto.EcsServiceSpecification ecsServiceSpecificationDto =
        (software.wings.beans.dto.EcsServiceSpecification) dtoSerializer.asObject(
            originalSerializer.asBytes(ecsServiceSpecification));

    assertThat(ecsServiceSpecificationDto.getServiceId()).isEqualTo(ecsServiceSpecification.getServiceId());
    assertThat(ecsServiceSpecificationDto.getServiceSpecJson()).isEqualTo(ecsServiceSpecification.getServiceSpecJson());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromDtoToOriginal() {
    software.wings.beans.dto.EcsServiceSpecification ecsServiceSpecificationDto =
        software.wings.beans.dto.EcsServiceSpecification.builder()
            .serviceId("someServiceId")
            .serviceSpecJson("{ \"isThisJson\": true }")
            .build();

    // serialize and deserialize to dto
    EcsServiceSpecification ecsServiceSpecification =
        (EcsServiceSpecification) originalSerializer.asObject(dtoSerializer.asBytes(ecsServiceSpecificationDto));

    assertThat(ecsServiceSpecification.getServiceId()).isEqualTo(ecsServiceSpecificationDto.getServiceId());
    assertThat(ecsServiceSpecification.getServiceSpecJson()).isEqualTo(ecsServiceSpecificationDto.getServiceSpecJson());
  }

  public static class OriginalRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(EcsServiceSpecification.class, REGISTRATION_ID);
    }
  }

  public static class DtoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(software.wings.beans.dto.EcsServiceSpecification.class, REGISTRATION_ID);
    }
  }
}
