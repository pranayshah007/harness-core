package io.harness.mappers;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.Instance;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class InstanceMapperTest {
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_InstanceDTO() {
    Instance instance = Instance.builder()
                            .infraIdentifier("Identifier")
                            .infraName("Name")
                            .createdAt(123L)
                            .lastModifiedAt(123l)
                            .instanceInfo(K8sInstanceInfo.builder().build())
                            .build();
    InstanceDTO instanceDTO = InstanceMapper.toDTO(instance);
    assertThat(instanceDTO.getInfraIdentifier()).isEqualTo("Identifier");
    assertThat(instanceDTO.getInfraName()).isEqualTo("Name");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_InstanceDTO_NullCheck() {
    Instance instance =
        Instance.builder().createdAt(123L).lastModifiedAt(123l).instanceInfo(K8sInstanceInfo.builder().build()).build();
    InstanceDTO instanceDTO = InstanceMapper.toDTO(instance);
    assertThat(instanceDTO.getInfraIdentifier()).isNull();
    assertThat(instanceDTO.getInfraName()).isNull();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_Instance() {
    InstanceDTO instanceDTO = InstanceDTO.builder()
                                  .infraIdentifier("Identifier")
                                  .infraName("Name")
                                  .instanceInfoDTO(K8sInstanceInfoDTO.builder().build())
                                  .build();
    Instance instance = InstanceMapper.toEntity(instanceDTO);
    assertThat(instance.getInfraIdentifier()).isEqualTo("Identifier");
    assertThat(instance.getInfraName()).isEqualTo("Name");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_Instance_NullCheck() {
    InstanceDTO instanceDTO = InstanceDTO.builder().instanceInfoDTO(K8sInstanceInfoDTO.builder().build()).build();
    Instance instance = InstanceMapper.toEntity(instanceDTO);
    assertThat(instance.getInfraIdentifier()).isNull();
    assertThat(instance.getInfraName()).isNull();
  }
}