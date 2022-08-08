/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BRETT;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Service;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class KubernetesContainerTaskTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Inject private HPersistence persistence;
  @Inject @InjectMocks private ServiceResourceService serviceResourceService;

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCheckDaemonSet() {
    KubernetesContainerTask kubernetesContainerTask = new KubernetesContainerTask();
    kubernetesContainerTask.setAdvancedConfig("a: b\n kind: DaemonSet\nfoo: bar");
    assertThat(KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig())).isTrue();
    kubernetesContainerTask.setAdvancedConfig("a: b\n kind: \"DaemonSet\"\nfoo: bar");
    assertThat(KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig())).isTrue();
    kubernetesContainerTask.setAdvancedConfig("a: b\n kind:DaemonSet\nfoo: bar");
    assertThat(KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig())).isTrue();
    kubernetesContainerTask.setAdvancedConfig("a: b\n kind:  DaemonSet\nfoo: bar");
    assertThat(KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig())).isTrue();
    kubernetesContainerTask.setAdvancedConfig("a: b\n kind:\t\"DaemonSet\"\nfoo: bar");
    assertThat(KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig())).isTrue();
    kubernetesContainerTask.setAdvancedConfig("a: b\n kind: Deployment\nfoo: bar");
    assertThat(KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig())).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldSetAdvancedConfig() {
    KubernetesContainerTask kubernetesContainerTask = new KubernetesContainerTask();

    kubernetesContainerTask.setAdvancedConfig(null);
    assertThat(kubernetesContainerTask.getAdvancedConfig()).isNull();

    kubernetesContainerTask.setAdvancedConfig("one line");
    assertThat(kubernetesContainerTask.getAdvancedConfig()).isEqualTo("one line");

    kubernetesContainerTask.setAdvancedConfig("a\nb");
    assertThat(kubernetesContainerTask.getAdvancedConfig()).isEqualTo("a\nb");

    kubernetesContainerTask.setAdvancedConfig("a \nb");
    assertThat(kubernetesContainerTask.getAdvancedConfig()).isEqualTo("a\nb");

    kubernetesContainerTask.setAdvancedConfig("a    \n b   \n  c");
    assertThat(kubernetesContainerTask.getAdvancedConfig()).isEqualTo("a\n b\n  c");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void validateDomainNameReplacement() {
    String domainName = "abc.xyz.com";
    Pattern pattern = ContainerTaskCommons.compileRegexPattern(domainName);

    String imageNameText = "   image: abc.xyz.com/${DOCKER_IMAGE_NAME}";
    Matcher matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(true);

    imageNameText = "   image: \"abc.xyz.com/${DOCKER_IMAGE_NAME}\"";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(true);

    imageNameText = "   \"image\": \"abc.xyz.com/${DOCKER_IMAGE_NAME}\"";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(true);

    imageNameText = "   \"image\": abc.xyz.com/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(true);

    imageNameText = "   \"image\": abcdxyz.com/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   \"image\": pqr*/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   \"image\": abc.xyz.com/(+/)/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   image: ${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   image: \"${DOCKER_IMAGE_NAME}\"";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   \"image\": \"${DOCKER_IMAGE_NAME}\"";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   \"image\": ${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    domainName = "abc*";
    pattern = ContainerTaskCommons.compileRegexPattern(domainName);

    imageNameText = "   image: abc*/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(true);

    imageNameText = "   image: abc/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   image: \"abc.xyz.com/${DOCKER_IMAGE_NAME}\"";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   \"image\": \"abc.xyz.com/${DOCKER_IMAGE_NAME}\"";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);

    imageNameText = "   image: abc.xyz.com/${DOCKER_IMAGE_NAME}";
    matcher = pattern.matcher(imageNameText);
    assertThat(matcher.find()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDecimalCPUInKubernetesContainerTask() {
    persistence.save(Service.builder().uuid(SERVICE_ID).appId(APP_ID).build());
    persistence.save(anApplication().uuid(APP_ID).build());

    KubernetesContainerTask kubernetesContainerTask = new KubernetesContainerTask();
    ContainerDefinition containerDefinition = ContainerDefinition.builder().memory(256).cpu(0.5).build();
    kubernetesContainerTask.setContainerDefinitions(Lists.newArrayList(containerDefinition));
    kubernetesContainerTask.setServiceId(SERVICE_ID);
    kubernetesContainerTask.setAppId(APP_ID);

    ContainerTask containerTask = serviceResourceService.createContainerTask(kubernetesContainerTask, false);
    assertThat(containerTask.getContainerDefinitions().get(0).getCpu()).isEqualTo(0.5);
  }
}
