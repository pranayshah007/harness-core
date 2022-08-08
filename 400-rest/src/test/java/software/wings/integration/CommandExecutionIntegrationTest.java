/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.delegate.beans.FileBucket.ARTIFACTS;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.shell.AccessType.USER_PASSWORD;

import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.artifact.ArtifactFile.Builder.anArtifactFile;
import static software.wings.beans.command.CommandUnitType.SCP;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AppContainer;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.ScpCommandUnit.ScpFileCategory;
import software.wings.beans.dto.Command;
import software.wings.beans.infrastructure.Host;
import software.wings.dl.WingsPersistence;
import software.wings.rules.Integration;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceCommandExecutorService;

import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by anubhaw on 6/2/16.
 */
@Integration
public class CommandExecutionIntegrationTest extends WingsBaseTest {
  private static final String HOST_NAME = "192.168.1.53";
  private static final String USER = "ssh_user";
  private static final char[] PASSWORD = "Wings@123".toCharArray();
  private static final SettingAttribute HOST_CONN_ATTR_PWD =
      aSettingAttribute().withValue(aHostConnectionAttributes().withAccessType(USER_PASSWORD).build()).build();
  private static final Host HOST = aHost()
                                       .withAppId(APP_ID)
                                       .withEnvId(ENV_ID)
                                       .withHostName(HOST_NAME)
                                       .withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid())
                                       .build();
  private static final ServiceTemplate SERVICE_TEMPLATE =
      aServiceTemplate().withUuid(TEMPLATE_ID).withName(TEMPLATE_NAME).withServiceId(SERVICE_ID).build();
  /**
   * The constant SERVICE_INSTANCE.
   */
  public static final ServiceInstance SERVICE_INSTANCE = aServiceInstance()
                                                             .withAppId(APP_ID)
                                                             .withEnvId(ENV_ID)
                                                             .withHost(HOST)
                                                             .withServiceTemplate(SERVICE_TEMPLATE)
                                                             .build();
  /**
   * The Service command executor service.
   */
  @Inject ServiceCommandExecutorService serviceCommandExecutorService;
  /**
   * The File service.
   */
  @Inject FileService fileService;
  /**
   * The Wings persistence.
   */
  @Inject WingsPersistence wingsPersistence;
  private CommandExecutionContext context =
      CommandExecutionContext.Builder.aCommandExecutionContext()
          .activityId(ACTIVITY_ID)
          .runtimePath("$HOME/apps")
          .executionCredential(aSSHExecutionCredential().withSshUser(USER).withSshPassword(PASSWORD).build())
          .build();

  private Command command =
      Command.builder()
          .name("INSTALL")
          .commandUnits(asList(
              anExecCommandUnit().withName("Delete start and stop script").withCommandString("rm -f ./bin/*").build(),
              anExecCommandUnit()
                  .withName("Create service startup file")
                  .withCommandString("mkdir -p bin && echo 'sh service && echo \"service started\" ' > ./bin/start.sh")
                  .build(),
              anExecCommandUnit()
                  .withName("Create stop file")
                  .withCommandString("echo 'echo \"service successfully stopped\"'  > ./bin/stop.sh")
                  .build(),
              anExecCommandUnit()
                  .withName("Make start/stop script executable")
                  .withCommandString("chmod +x ./bin/*")
                  .build(),
              anExecCommandUnit().withName("Exec").withCommandString("./bin/stop.sh").build(),
              ScpCommandUnit.Builder.aScpCommandUnit()
                  .withName("Copy_ARTIFACT")
                  .withCommandUnitType(SCP)
                  .withFileCategory(ScpFileCategory.ARTIFACTS)
                  .build(),
              anExecCommandUnit().withName("EXEC").withCommandString("./bin/start.sh").build()))
          .build();

  /**
   * Sets the up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    wingsPersistence.getCollection(AppContainer.class).drop();
    String uuid = fileService.saveFile(anArtifactFile().withName("app").build(),
        new ByteArrayInputStream("echo 'hello world'".getBytes(StandardCharsets.UTF_8)), ARTIFACTS);
    ArtifactFile artifactFile = anArtifactFile().withFileUuid(uuid).withName("service").build();
    context.setArtifactFiles(asList(artifactFile));
  }

  /**
   * Should execute command.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldExecuteCommand() {
    CommandExecutionStatus commandExecutionStatus = serviceCommandExecutorService.execute(command, context);
    command.getCommandUnits().forEach(
        commandUnit -> assertThat(commandUnit.getCommandExecutionStatus()).isEqualTo(SUCCESS));
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
  }

  /**
   * Should capture failed execution command unit.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldCaptureFailedExecutionCommandUnit() {
    ((ExecCommandUnit) command.getCommandUnits().get(6)).setCommandString("INVALID_COMMAND");
    CommandExecutionStatus commandExecutionStatus = serviceCommandExecutorService.execute(command, context);
    for (int i = 0; i < command.getCommandUnits().size() - 1; i++) {
      assertThat(command.getCommandUnits().get(i).getCommandExecutionStatus()).isEqualTo(SUCCESS);
    }
    assertThat(command.getCommandUnits().get(6).getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(commandExecutionStatus).isEqualTo(FAILURE);
  }
}
