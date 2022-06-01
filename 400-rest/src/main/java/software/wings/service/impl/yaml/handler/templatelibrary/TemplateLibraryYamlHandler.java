/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.templatelibrary;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.Variable.VariableBuilder.aVariable;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.NoResultFoundException;

import software.wings.beans.Application;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.yaml.templatelibrary.TemplateLibraryYaml;
import software.wings.yaml.templatelibrary.TemplateVariableYaml;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

@OwnedBy(CDC)
@Slf4j
public abstract class TemplateLibraryYamlHandler<Y extends TemplateLibraryYaml> extends BaseYamlHandler<Y, Template> {
  @Inject private YamlHandlerFactory yamlHandlerFactory;
  @Inject private YamlHelper yamlHelper;
  @Inject private TemplateService templateService;
  @Inject private AppService appService;
  @Inject private TemplateGalleryService templateGalleryService;

  public static List<TemplateVariableYaml> variablesToTemplateVariableYaml(List<Variable> variables) {
    return ListUtils.emptyIfNull(variables)
        .stream()
        .map(variable
            -> TemplateVariableYaml.builder()
                   .description(variable.getDescription())
                   .name(variable.getName())
                   .value(variable.getValue())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public void delete(ChangeContext<Y> changeContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    final String appId = getApplicationId(accountId, yamlFilePath);
    String templateName = yamlHelper.extractTemplateLibraryName(yamlFilePath, appId);
    TemplateFolder templateFolder = yamlHelper.getTemplateFolderForYamlFilePath(accountId, yamlFilePath, appId);
    if (templateFolder == null) {
      return;
    }
    Template template = templateService.findByFolder(templateFolder, templateName, appId);
    if (template == null) {
      return;
    }
    templateService.delete(accountId, template.getUuid());
  }

  @VisibleForTesting
  String getApplicationId(String accountId, String yamlPath) {
    final String appName = yamlHelper.getAppName(yamlPath);
    if (EmptyPredicate.isNotEmpty(appName)) {
      final Application application = appService.getAppByName(accountId, appName);
      if (application == null) {
        throw NoResultFoundException.newBuilder()
            .message("Cannot find application by name :" + appName)
            .level(Level.ERROR)
            .code(ErrorCode.INVALID_ARGUMENT)
            .build();
      }
      return application.getUuid();
    }
    return GLOBAL_APP_ID;
  }

  @Override
  public Template get(String accountId, String yamlFilePath) {
    final String appId = getApplicationId(accountId, yamlFilePath);
    String templateName = yamlHelper.extractTemplateLibraryName(yamlFilePath, appId);
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey());
    TemplateFolder templateFolder =
        yamlHelper.ensureTemplateFolder(accountId, yamlFilePath, appId, templateGallery.getUuid());
    return templateService.findByFolder(templateFolder, templateName, appId);
  }

  protected void toYaml(Y yaml, Template bean) {
    yaml.setHarnessApiVersion(getHarnessApiVersion());
    yaml.setType(bean.getType());
    yaml.setVariables(variablesToTemplateVariableYaml(bean.getVariables()));
  }

  @Override
  public Template upsertFromYaml(ChangeContext<Y> changeContext, List<ChangeContext> changeSetContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    final String appId = getApplicationId(accountId, yamlFilePath);
    String templateName = yamlHelper.extractTemplateLibraryName(yamlFilePath, appId);
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey());
    TemplateFolder templateFolder =
        yamlHelper.ensureTemplateFolder(accountId, yamlFilePath, appId, templateGallery.getUuid());
    Template template = templateService.findByFolder(templateFolder, templateName, appId);
    if (template != null) {
      return updateTemplate(template, changeContext, changeSetContext);
    }
    Template newTemplate = toBean(changeContext, appId, templateFolder, changeSetContext);
    return templateService.save(newTemplate);
  }

  public static List<Variable> templateVariableYamlToVariable(List<TemplateVariableYaml> variablesYaml) {
    return ListUtils.emptyIfNull(variablesYaml)
        .stream()
        .map(variableYaml
            -> aVariable()
                   .name(variableYaml.getName())
                   .value(variableYaml.getValue())
                   .description(variableYaml.getDescription())
                   .mandatory(false)
                   .type(VariableType.TEXT)
                   .fixed(false)
                   .build())
        .collect(Collectors.toList());
  }

  protected abstract void setBaseTemplate(
      Template template, ChangeContext<Y> changeContext, List<ChangeContext> changeSetContext);

  private Template updateTemplate(
      Template template, ChangeContext<Y> changeContext, List<ChangeContext> changeSetContext) {
    TemplateLibraryYaml yaml = changeContext.getYaml();
    template.setDescription(yaml.getDescription());
    template.setVariables(templateVariableYamlToVariable(yaml.getVariables()));
    template.setSyncFromGit(changeContext.getChange().isSyncFromGit());
    setBaseTemplate(template, changeContext, changeSetContext);
    return templateService.update(template);
  }

  private Template toBean(ChangeContext<Y> changeContext, String appId, TemplateFolder templateFolder,
      List<ChangeContext> changeSetContext) {
    Y yaml = changeContext.getYaml();
    String yamlFilePath = changeContext.getChange().getFilePath();
    String templateName = yamlHelper.extractTemplateLibraryName(yamlFilePath, appId);
    Template template = Template.builder()
                            .name(templateName)
                            .appId(appId)
                            .accountId(changeContext.getChange().getAccountId())
                            .type(yaml.getType())
                            .description(yaml.getDescription())
                            .folderId(templateFolder.getUuid())
                            .variables(templateVariableYamlToVariable(yaml.getVariables()))
                            .build();
    template.setSyncFromGit(changeContext.getChange().isSyncFromGit());
    setBaseTemplate(template, changeContext, changeSetContext);
    return template;
  }

  @Override
  public Class getYamlClass() {
    return TemplateLibraryYaml.class;
  }
}
