/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.rule.OwnerRule.JOHANNES;

import static software.wings.beans.command.ContainerSetupParams.MORPHIA_CLASS_NAME_FIELD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.command.ContainerSetupParams.ContainerSetupParamsKeys;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerSetupParamsTest extends CategoryTest {
  private final ContainerSetupParams params = new ContainerSetupParams();

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoThrowForNull() {
    params.PreLoad(null);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoThrowForNoContainerTask() {
    params.PreLoad(new BasicDBObject());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoThrowForContainerTaskNull() {
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, null);

    params.PreLoad(paramDBObject);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoThrowForNoClassName() {
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, new BasicDBObject());

    params.PreLoad(paramDBObject);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoThrowForClassNameNull() {
    DBObject containerTaskDBObject = new BasicDBObject();
    containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, null);
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, containerTaskDBObject);

    params.PreLoad(paramDBObject);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoChangesForUnknownClassName() {
    String initialClassName = "someRandomClassName";
    DBObject containerTaskDBObject = new BasicDBObject();
    containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, initialClassName);
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, containerTaskDBObject);

    params.PreLoad(paramDBObject);

    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isEqualTo(initialClassName);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoChangesForK8sDto() {
    String initialClassName = software.wings.beans.dto.KubernetesContainerTask.class.getCanonicalName();
    DBObject containerTaskDBObject = new BasicDBObject();
    containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, initialClassName);
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, containerTaskDBObject);

    params.PreLoad(paramDBObject);

    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isEqualTo(initialClassName);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadNoChangesForEcsDto() {
    String initialClassName = software.wings.beans.dto.EcsContainerTask.class.getCanonicalName();
    DBObject containerTaskDBObject = new BasicDBObject();
    containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, initialClassName);
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, containerTaskDBObject);

    params.PreLoad(paramDBObject);

    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isEqualTo(initialClassName);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadChangesClassNameForK8sOriginal() {
    String initialClassName = software.wings.beans.container.KubernetesContainerTask.class.getCanonicalName();
    DBObject containerTaskDBObject = new BasicDBObject();
    containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, initialClassName);
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, containerTaskDBObject);

    params.PreLoad(paramDBObject);

    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isNotEqualTo(initialClassName);
    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isEqualTo(software.wings.beans.dto.KubernetesContainerTask.class.getCanonicalName());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void TestPreLoadChangesClassNameForEcsOriginal() {
    String initialClassName = software.wings.beans.container.EcsContainerTask.class.getCanonicalName();
    DBObject containerTaskDBObject = new BasicDBObject();
    containerTaskDBObject.put(MORPHIA_CLASS_NAME_FIELD, initialClassName);
    DBObject paramDBObject = new BasicDBObject();
    paramDBObject.put(ContainerSetupParamsKeys.containerTask, containerTaskDBObject);

    params.PreLoad(paramDBObject);

    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isNotEqualTo(initialClassName);
    assertThat(((DBObject) paramDBObject.get(ContainerSetupParamsKeys.containerTask)).get(MORPHIA_CLASS_NAME_FIELD))
        .isEqualTo(software.wings.beans.dto.EcsContainerTask.class.getCanonicalName());
  }
}
