/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.freeze.beans.EntityConfig;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.Recurrence;
import io.harness.freeze.beans.RecurrenceSpec;
import io.harness.freeze.beans.RecurrenceType;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class NGFreezeDtoMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "oId";
  private final String PROJ_IDENTIFIER = "pId";
  private final String FREEZE_IDENTIFIER = "freezeId";

  private String yaml;

  FreezeConfigEntity freezeConfigEntity;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "projectFreezeConfig.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    freezeConfigEntity = FreezeConfigEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .identifier(FREEZE_IDENTIFIER)
                             .name(FREEZE_IDENTIFIER)
                             .yaml(yaml)
                             .type(FreezeType.MANUAL)
                             .freezeScope(Scope.PROJECT)
                             .build();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testToTemplateDto() {
    FreezeConfigEntity entity =
        NGFreezeDtoMapper.toFreezeConfigEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, FreezeType.MANUAL);
    assertThat(entity).isNotNull();
    assertThat(entity.getType()).isEqualTo(FreezeType.MANUAL);
    assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(entity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(entity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(entity.getYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testToTemplateDto1() {
    FreezeInfoConfig freezeInfoConfig =
        FreezeInfoConfig.builder().identifier("id").name("name").status(FreezeStatus.ENABLED).build();
    List<EntityConfig> entities = new LinkedList<>();
    List<FreezeEntityRule> freezeEntityRules = new LinkedList<>();
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    List<FreezeWindow> windows = new LinkedList<>();
    freezeInfoConfig.setDescription(ParameterField.<String>builder().value("desc").build());
    //    freezeInfoConfig.setOrgIdentifier("oId");
    //    freezeInfoConfig.setProjectIdentifier("pId");
    freezeInfoConfig.setRules(freezeEntityRules);
    freezeInfoConfig.setWindows(windows);
    EntityConfig entity = new EntityConfig();
    entity.setFilterType(FilterType.ALL);
    entity.setFreezeEntityType(FreezeEntityType.SERVICE);
    entity.setEntityReference(newArrayList("serv1", "serv2"));
    entities.add(entity);
    freezeEntityRule.setEntityConfigList(entities);
    freezeEntityRules.add(freezeEntityRule);

    FreezeConfig freezeConfig = FreezeConfig.builder().freezeInfoConfig(freezeInfoConfig).build();
    freezeConfig.setFreezeInfoConfig(freezeInfoConfig);
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("Asd");
    freezeWindow.setStartTime("st");
    freezeWindow.setTimeZone("timezone");
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.DAILY);
    RecurrenceSpec recurrenceSpec = new RecurrenceSpec();
    recurrenceSpec.setUntil("until");
    recurrence.setSpec(recurrenceSpec);
    freezeWindow.setRecurrence(recurrence);
    windows.add(freezeWindow);
    String entityYaml = NGFreezeDtoMapper.toYaml(freezeConfig);
    assertThat(entityYaml).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateYamlWithEmptyFreezeInfoConfig() {
    FreezeConfig freezeConfig = FreezeConfig.builder().build();
    assertThatThrownBy(()
                           -> NGFreezeDtoMapper.validateFreezeYaml(
                               freezeConfig, ORG_IDENTIFIER, PROJ_IDENTIFIER, FreezeType.MANUAL, Scope.PROJECT))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateYamlWithMultipleWindows() {
    FreezeInfoConfig freezeInfoConfig = FreezeInfoConfig.builder()
                                            .identifier("id")
                                            .name("name")
                                            .status(FreezeStatus.ENABLED)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .projectIdentifier(PROJ_IDENTIFIER)
                                            .build();
    List<FreezeWindow> windows = new LinkedList<>();
    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setStartTime("st");
    freezeWindow1.setTimeZone("timezone");
    FreezeWindow freezeWindow2 = new FreezeWindow();
    freezeWindow2.setStartTime("st");
    freezeWindow2.setTimeZone("timezone");
    windows.add(freezeWindow1);
    windows.add(freezeWindow2);
    freezeInfoConfig.setWindows(windows);
    FreezeConfig freezeConfig = FreezeConfig.builder().freezeInfoConfig(freezeInfoConfig).build();
    assertThatThrownBy(()
                           -> NGFreezeDtoMapper.validateFreezeYaml(
                               freezeConfig, ORG_IDENTIFIER, PROJ_IDENTIFIER, FreezeType.MANUAL, Scope.PROJECT))
        .isInstanceOf(InvalidRequestException.class);
  }
}
