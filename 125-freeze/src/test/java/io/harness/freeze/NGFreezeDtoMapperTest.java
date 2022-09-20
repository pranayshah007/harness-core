/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
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
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class NGFreezeDtoMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
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
        NGFreezeDtoMapper.toFreezeConfigEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    assertThat(entity).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testToTemplateDto1() {
    FreezeConfig freezeConfig = new FreezeConfig();
    FreezeInfoConfig freezeInfoConfig = new FreezeInfoConfig();
    List<EntityConfig> entities = new LinkedList<>();
    List<FreezeEntityRule> freezeEntityRules = new LinkedList<>();
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    List<FreezeWindow> windows = new LinkedList<>();
    freezeInfoConfig.setActive(FreezeStatus.ACTIVE);
    freezeInfoConfig.setDescription(ParameterField.<String>builder().value("desc").build());
    freezeInfoConfig.setIdentifier("id");
    freezeInfoConfig.setName("name");
    freezeInfoConfig.setOrgIdentifier("oId");
    freezeInfoConfig.setProjectIdentifier("pId");
    freezeInfoConfig.setRules(freezeEntityRules);
    freezeInfoConfig.setWindows(windows);
    EntityConfig entity = new EntityConfig();
    entity.setExpression("exp");
    entity.setFilterType(FilterType.ALL);
    entity.setTags(newArrayList("tag1", "tag2"));
    entity.setFreezeEntityType(FreezeEntityType.SERVICE);
    entity.setEntityReference(newArrayList("serv1", "serv2"));
    entities.add(entity);
    freezeEntityRule.setEntityConfigList(entities);
    freezeEntityRules.add(freezeEntityRule);
    freezeConfig.setFreezeInfoConfig(freezeInfoConfig);
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("Asd");
    freezeWindow.setStartTime("st");
    freezeWindow.setTimeZone(TimeZone.getDefault());
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.DAILY);
    RecurrenceSpec recurrenceSpec = new RecurrenceSpec();
    recurrenceSpec.setCount(1);
    recurrenceSpec.setExpression("exp");
    recurrenceSpec.setUntil("until");
    recurrence.setSpec(recurrenceSpec);
    freezeWindow.setRecurrence(recurrence);
    windows.add(freezeWindow);
    String entityYaml = NGFreezeDtoMapper.toYaml(freezeConfig);
    assertThat(entityYaml).isNotNull();
  }
}
