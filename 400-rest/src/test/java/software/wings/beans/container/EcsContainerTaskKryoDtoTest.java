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

public class EcsContainerTaskKryoDtoTest extends CategoryTest {
  private static final int REGISTRATION_ID = 421;

  private static final KryoSerializer originalSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(OriginalRegistrar.class)), true);
  private static final KryoSerializer dtoSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(DtoRegistrar.class)), true);

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromOriginalToDto() {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setServiceId("SomeServiceId");
    ecsContainerTask.setAdvancedConfig("SomeAdvancedConfig: true");
    ecsContainerTask.setContainerDefinitions(
        Arrays.asList(new ContainerDefinition(null, "Def1", null, null, null, null, null)));

    // serialize and deserialize to dto
    software.wings.beans.dto.EcsContainerTask ecsContainerTaskDto =
        (software.wings.beans.dto.EcsContainerTask) dtoSerializer.asObject(
            originalSerializer.asBytes(ecsContainerTask));

    assertThat(ecsContainerTaskDto.getDeploymentType()).isEqualTo(ecsContainerTask.getDeploymentType());
    assertThat(ecsContainerTaskDto.getServiceId()).isEqualTo(ecsContainerTask.getServiceId());
    assertThat(ecsContainerTaskDto.getAdvancedConfig()).isEqualTo(ecsContainerTask.getAdvancedConfig());
    assertThat(ecsContainerTaskDto.getContainerDefinitions().size())
        .isEqualTo(ecsContainerTask.getContainerDefinitions().size());
    assertThat(ecsContainerTaskDto.getContainerDefinitions().get(0).getName())
        .isEqualTo(ecsContainerTask.getContainerDefinitions().get(0).getName());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromDtoToOriginal() {
    software.wings.beans.dto.EcsContainerTask ecsContainerTaskDto =
        software.wings.beans.dto.EcsContainerTask.builder()
            .serviceId("SomeServiceId")
            .advancedConfig("SomeAdvancedConfig: true")
            .deploymentType("SomeDeploymentType")
            .containerDefinitions(Arrays.asList(new ContainerDefinition(null, "Def1", null, null, null, null, null)))
            .build();

    // serialize and deserialize to dto
    EcsContainerTask ecsContainerTask =
        (EcsContainerTask) originalSerializer.asObject(dtoSerializer.asBytes(ecsContainerTaskDto));

    assertThat(ecsContainerTask.getDeploymentType()).isEqualTo(ecsContainerTaskDto.getDeploymentType());
    assertThat(ecsContainerTask.getServiceId()).isEqualTo(ecsContainerTaskDto.getServiceId());
    assertThat(ecsContainerTask.getAdvancedConfig()).isEqualTo(ecsContainerTaskDto.getAdvancedConfig());
    assertThat(ecsContainerTask.getContainerDefinitions().size())
        .isEqualTo(ecsContainerTaskDto.getContainerDefinitions().size());
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getName())
        .isEqualTo(ecsContainerTaskDto.getContainerDefinitions().get(0).getName());
  }

  public static void registerCommons(Kryo kryo) {
    // These IDs are not related to prod IDs.
    int id = 10000;
    kryo.register(ContainerDefinition.class, id++);
  }

  public static class OriginalRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      registerCommons(kryo);
      kryo.register(EcsContainerTask.class, REGISTRATION_ID);
    }
  }

  public static class DtoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      registerCommons(kryo);
      kryo.register(software.wings.beans.dto.EcsContainerTask.class, REGISTRATION_ID);
    }
  }
}
