/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.seedata;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;

import static java.util.Arrays.asList;

import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.migrations.SeedDataMigration;

import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateReference;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TomcatInstallCommandMigration implements SeedDataMigration {
  @Inject private TemplateService templateService;
  @Inject private TemplateGalleryService templateGalleryService;

  @Override
  public void migrate() {
    // Get Tomcat Install template for global account
    // validate Harness global gallery exists
    try {
      TemplateGallery harnessTemplateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
      if (harnessTemplateGallery == null) {
        log.info("Harness global gallery does not exist. Not updating Tomcat Install Command templates");
        return;
      }

      Template globalInstallTemplate = templateService.fetchTemplateByKeywordsForAccountGallery(
          GLOBAL_ACCOUNT_ID, new HashSet<>(asList("ssh", "war", "install", "tomcat")));
      if (globalInstallTemplate != null) {
        log.info("Migrating Default Tomcat Install Command to new format in account [{}]", GLOBAL_ACCOUNT_ID);
        // Update Tomcat Install template for global account
        updateTemplate(globalInstallTemplate);
        log.info("Migrated Default Tomcat Install Command to new format in account [{}]", GLOBAL_ACCOUNT_ID);
        // get it again after update to fetch latest version
        globalInstallTemplate = templateService.get(globalInstallTemplate.getUuid());
        // Get all templates that have referencedTemplateId = global_install_template_id
        List<Template> templates =
            templateService.fetchTemplatesWithReferencedTemplateId(globalInstallTemplate.getUuid());
        // Update each template
        for (Template template : templates) {
          log.info("Migrating Default Tomcat Install Command to new format in account [{}]", template.getAccountId());
          template.setReferencedTemplateVersion(globalInstallTemplate.getVersion());
          log.info("Migrated Default Tomcat Install Command to new format in account [{}]", template.getAccountId());
          updateTemplate(template);
        }
        log.info("Done migrating Default Tomcat Install Command to new format in all accounts...");
      } else {
        log.error("Tomcat Install Command template not found in Global account");
      }
    } catch (WingsException e) {
      ExceptionLogger.logProcessedMessages(e, MANAGER, log);
      log.error("Tomcat Install Command Migration failed: ", e);
    } catch (Exception e) {
      log.error("Tomcat Install Command Migration failed: ", e);
    }
  }

  private void updateTemplate(Template existingTemplate) {
    if (existingTemplate != null) {
      log.info("Tomcat Install Command template found in [{}] account", existingTemplate.getAccountId());
      for (CommandUnit commandUnit : ((SshCommandTemplate) existingTemplate.getTemplateObject()).getCommandUnits()) {
        if (commandUnit instanceof Command) { // updating linked start and stop commands
          // fetch linked template
          Template existingSubTemplate = templateService.get(((Command) commandUnit).getReferenceUuid());
          if (existingSubTemplate != null) {
            // set TemplateReference since its required in new format
            ((Command) commandUnit)
                .setTemplateReference(TemplateReference.builder()
                                          .templateUuid(existingSubTemplate.getUuid())
                                          .templateVersion(existingSubTemplate.getVersion())
                                          .build());
          }
          // set Template variables for command unit
          Variable variable = aVariable().name("RuntimePath").type(VariableType.TEXT).value("${RuntimePath}").build();
          ((Command) commandUnit).setTemplateVariables(asList(variable));
        }
      }
      existingTemplate = templateService.update(existingTemplate);
      log.info("Tomcat Install Command template updated in account [{}]", existingTemplate.getAccountId());
    } else {
      log.error("Tomcat Install Command template not found");
    }
  }
}
