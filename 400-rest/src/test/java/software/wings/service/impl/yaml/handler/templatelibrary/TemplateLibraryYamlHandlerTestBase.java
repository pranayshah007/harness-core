/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.templatelibrary;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.persistence.HPersistence;
import io.harness.yaml.BaseYaml;

import software.wings.beans.template.TemplateGallery;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.variable.VariableYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Rule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public abstract class TemplateLibraryYamlHandlerTestBase extends YamlHandlerTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock protected YamlHelper yamlHelper;
  @Mock protected YamlHandlerFactory yamlHandlerFactory;
  @Mock protected EnvironmentService environmentService;
  @Mock protected YamlPushService yamlPushService;
  @Inject private TemplateGalleryService templateGalleryService;
  @InjectMocks @Inject protected VariableYamlHandler variableYamlHandler;
  @Inject private HPersistence persistence;

  public static String TEMPLATE_GALLERY_UUID = "uuid";
  protected void setup(String yamlFilePath, String templateName) {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(GLOBAL_APP_ID);
    when(yamlHelper.extractTemplateLibraryName(yamlFilePath, GLOBAL_APP_ID)).thenReturn(templateName);
    when(yamlHandlerFactory.getYamlHandler(YamlType.VARIABLE)).thenReturn(variableYamlHandler);
    when(yamlHelper.getTemplateFolderForYamlFilePath(GLOBAL_ACCOUNT_ID, yamlFilePath, GLOBAL_APP_ID)).thenReturn(null);
    TemplateGallery templateGallery = TemplateGallery.builder()
                                          .name("Gallery Name")
                                          .galleryKey(templateGalleryService.getAccountGalleryKey().name())
                                          .accountId(GLOBAL_ACCOUNT_ID)
                                          .uuid(TEMPLATE_GALLERY_UUID)
                                          .build();
    persistence.save(templateGallery);
  }

  protected <Y extends BaseYaml, H extends BaseYamlHandler> ChangeContext<Y> getChangeContext(
      String yamlContent, String yamlFilePath, H yamlHandler) {
    // Invalid yaml path
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(yamlContent)
                                      .withSyncFromGit(true)
                                      .build();

    ChangeContext<Y> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.GLOBAL_TEMPLATE_LIBRARY);
    changeContext.setYamlSyncHandler(yamlHandler);
    return changeContext;
  }

  protected <Y extends BaseYaml, H extends TemplateLibraryYamlHandler> void testFailures(String validYamlContent,
      String validYamlFilePath, String invalidYamlContent, String invalidYamlFilePath, H yamlHandler,
      Class<Y> yamlClass) throws IOException {
    final ChangeContext<Y> changeContext = getChangeContext(validYamlContent, invalidYamlFilePath, yamlHandler);

    Y yamlObject = null;
    yamlObject = (Y) getYaml(validYamlContent, yamlClass);

    changeContext.setYaml(yamlObject);

    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, asList(changeContext)))
        .isInstanceOf(Exception.class);

    // Invalid yaml content
    final ChangeContext<Y> changeContext1 = getChangeContext(invalidYamlContent, validYamlFilePath, yamlHandler);

    yamlObject = (Y) getYaml(invalidYamlContent, yamlClass);

    changeContext1.setYaml(yamlObject);

    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext1, asList(changeContext1)))
        .isInstanceOf(NullPointerException.class);
    assertThat(changeContext.getChange().isSyncFromGit()).isEqualTo(true);
  }
}
