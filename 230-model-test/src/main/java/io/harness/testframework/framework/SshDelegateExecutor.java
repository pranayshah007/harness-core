/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.framework;

import static io.harness.delegate.beans.Delegate.DelegateKeys;
import static io.harness.mongo.MongoUtils.setUnset;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.resource.Project;
import io.harness.rest.RestResponse;
import io.harness.threading.Poller;
import io.harness.version.VersionInfoManager;

import software.wings.beans.Account;
import software.wings.beans.DelegateStatus;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.HostConfig;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class SshDelegateExecutor {
  private boolean failedAlready;
  private static final String LOCAL_STORAGE = "local-storage";
  private static final String WINGS_DELEGATES = "wingsdelegates";
  private static final String WINGS_WATCHERS = "wingswatchers";
  private static final String WATCHER_TXT = "watcherlocal.txt";
  private static final String DELEGATE_TXT = "delegatelocal.txt";
  private static final String JRE_DOWNLOAD_URL =
      "https://app.harness.io/storage/wingsdelegates/jre/openjdk-8u242/jre_x64_linux_8u242b08.tar.gz";
  private static final String JRE_FILE = "jre_x64_linux_8u242b08.tar.gz";
  private static final String ID_KEY = "_id";
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String UBUNTU_IMAGE = "ubuntu:18.04";
  public String delegateUuid;

  @Inject private DelegateService delegateService;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private WingsPersistence wingsPersistence;

  public void ensureSshDelegate(Account account, String bearerToken, Class clazz)
      throws IOException, InterruptedException, DockerException, DockerCertificateException {
    if (!isHealthy(account.getUuid(), bearerToken)) {
      startSshDelegate(account, bearerToken, clazz);
    }
  }

  private void startSshDelegate(Account account, String bearerToken, Class clazz)
      throws IOException, InterruptedException, DockerException, DockerCertificateException {
    if (failedAlready) {
      return;
    }
    createLocalStorageFiles(clazz);

    try {
      DockerClient docker = DefaultDockerClient.fromEnv().build();

      docker.pull(UBUNTU_IMAGE);
      // Create container
      final ContainerConfig containerConfig = ContainerConfig.builder()
                                                  .hostConfig(HostConfig.builder()
                                                                  .binds(getDockerVolume(clazz))
                                                                  .memory(Long.valueOf("7516188919"))
                                                                  .networkMode("host")
                                                                  .build())
                                                  .image(UBUNTU_IMAGE)
                                                  .cmd("sh", "-c", "while :; do sleep 1; done")
                                                  .build();
      final ContainerCreation creation = docker.createContainer(containerConfig, "ssh_delegate");
      String containerId = creation.id();
      // Start container
      docker.startContainer(containerId);
      final String curlCommand = "curl -k -o harness-delegate.tar.gz \""
          + getDownloadURL(account.getUuid(), bearerToken) + "&delegateName=ssh-delegate"
          + "\"";
      log.info("Curl command = " + curlCommand);

      String[] commands = {
          "apt-get update",
          "apt-get install -y curl",
          curlCommand,
          "tar -xvf harness-delegate.tar.gz",
      };

      executeDockerCommands(docker, containerId, commands);

      if (isMac()) {
        String version = getVersion();
        commands = new String[] {"sed -i 's/localhost/docker.for.mac.localhost/g' harness-delegate/start.sh",
            "sed -i 's/localhost/docker.for.mac.localhost/g' harness-delegate/delegate.sh",
            "mkdir harness-delegate/" + version, "cp harness-delegate/delegate.sh harness-delegate/" + version + "/"};
        executeDockerCommands(docker, containerId, commands);
      }

      commands = new String[] {"ulimit -n 10000", "cd harness-delegate && ./start.sh"};
      executeDockerCommands(docker, containerId, commands);

      Poller.pollFor(ofMinutes(2), ofSeconds(20), () -> isHealthy(account.getUuid(), bearerToken));
      updateDelegateSelector(account.getUuid(), bearerToken);

    } catch (DockerException | InterruptedException | DockerCertificateException exception) {
      failedAlready = true;
      throw exception;
    }
  }

  private void executeDockerCommands(DockerClient docker, String id, String[] commands)
      throws DockerException, InterruptedException {
    for (String command : commands) {
      String[] commandArray = {"sh", "-c", command};
      ExecCreation execCreation = docker.execCreate(
          id, commandArray, DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
      LogStream output = docker.execStart(execCreation.id());
      String execOutput = output.readFully();
      log.info("Command output : " + execOutput);
    }
  }

  private String getDownloadURL(String accountId, String bearerToken) {
    String downLoadUrl = Setup.portal()
                             .auth()
                             .oauth2(bearerToken)
                             .queryParam("accountId", accountId)
                             .get("/setup/delegates/downloadUrl")
                             .jsonPath()
                             .getString("resource.downloadUrl");
    if (isMac()) {
      downLoadUrl = downLoadUrl.replace("localhost", "docker.for.mac.localhost");
    }
    return downLoadUrl;
  }

  private boolean isHealthy(String accountId, String bearerToken) {
    try {
      RestResponse<DelegateStatus> delegateStatusResponse =
          Setup.portal()
              .auth()
              .oauth2(bearerToken)
              .queryParam(DelegateKeys.accountId, accountId)
              .get("/setup/delegates/status")
              .as(new GenericType<RestResponse<DelegateStatus>>() {}.getType());

      DelegateStatus delegateStatus = delegateStatusResponse.getResource();
      if (!delegateStatus.getDelegates().isEmpty()) {
        for (DelegateStatus.DelegateInner delegateInner : delegateStatus.getDelegates()) {
          long lastMinuteMillis = System.currentTimeMillis() - 60000;
          if (delegateInner.getDelegateName().equals("ssh-delegate")) {
            if (delegateInner.getStatus() == DelegateInstanceStatus.ENABLED
                && delegateInner.getLastHeartBeat() > lastMinuteMillis) {
              this.delegateUuid = delegateInner.getUuid();
              return true;
            }
          }
        }
      }
      return false;
    } catch (Exception e) {
      log.error("Failed to get status of delegate(s)", e);
      return false;
    }
  }

  public void updateDelegateSelector(String accountId, String bearerToken) {
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam(DelegateKeys.accountId, accountId)
        .body("{\"tags\":[\"ssh-delegate\"]}")
        .put("setup/delegates/" + this.delegateUuid + "/tags");
  }

  private void createLocalStorageFiles(Class clazz) {
    String directoryPath = Project.rootDirectory(clazz);
    File dir = new File(directoryPath, LOCAL_STORAGE);
    final Path watcherMetadataFile = Paths.get(dir.getPath(), WINGS_WATCHERS, WATCHER_TXT);
    final Path delegateMetadataFile = Paths.get(dir.getPath(), WINGS_DELEGATES, DELEGATE_TXT);

    try {
      if (dir.exists()) {
        FileUtils.cleanDirectory(dir);
        FileUtils.forceDelete(dir);
      }
      String version = getVersion();
      createMetadataFile(watcherMetadataFile.toString(),
          version + " "
              + "watcher/watcher.jar");
      createMetadataFile(delegateMetadataFile.toString(),
          version + " "
              + "delegate/delegate.jar");
      copyDelegateAndWatcherJars(directoryPath);
      publishLocalDelegate(version);
    } catch (IOException e) {
      log.error("Local storage creation failed", e);
    }
  }

  private void createMetadataFile(String filename, String fileContent) throws IOException {
    File file = new File(filename);
    FileUtils.writeStringToFile(file, fileContent, StandardCharsets.UTF_8.name());
  }

  private String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  private void copyDelegateAndWatcherJars(String directoryPath) throws IOException {
    try {
      final File directory = new File(directoryPath);
      final Path delegateJarSource = Paths.get(directory.getPath(), "260-delegate", "target", "delegate-capsule.jar");
      final Path watcherJarSource = Paths.get(directory.getPath(), "960-watcher", "target", "watcher-capsule.jar");
      final Path watcherJarDestination =
          Paths.get(directory.getPath(), LOCAL_STORAGE, WINGS_WATCHERS, "watcher", "watcher.jar");
      final Path delegateJarDestination =
          Paths.get(directory.getPath(), LOCAL_STORAGE, WINGS_DELEGATES, "delegate", "delegate.jar");
      final Path jreDestination =
          Paths.get(directory.getPath(), LOCAL_STORAGE, WINGS_DELEGATES, "jre", "openjdk-8u242", JRE_FILE);

      FileUtils.copyFile(new File(delegateJarSource.toString()), new File(delegateJarDestination.toString()));
      FileUtils.copyFile(new File(watcherJarSource.toString()), new File(watcherJarDestination.toString()));
      // Download JRE and copy to local-storage
      FileUtils.copyURLToFile(new URL(JRE_DOWNLOAD_URL), new File(jreDestination.toString()), 60000, 60000);
    } catch (IOException e) {
      log.error("Unable to copy Jars", e);
    }
  }

  private static String getDockerVolume(Class clazz) throws IOException {
    String directoryPath = Project.rootDirectory(clazz);
    File dir = new File(directoryPath);
    String volume = Paths.get(dir.getPath(), LOCAL_STORAGE).toString();
    return volume + ":/local-storage";
  }

  private void publishLocalDelegate(String version) {
    try {
      Query<Account> globalAccountQuery = wingsPersistence.createQuery(Account.class).filter(ID_KEY, GLOBAL_ACCOUNT_ID);
      Account globalAccount = globalAccountQuery.get();
      if (globalAccount == null) {
        throw new RuntimeException("Global Account settings not found. Run DataGen Application");
      } else {
        log.info("Publishing Local delegate");
        UpdateOperations<Account> ops = wingsPersistence.createUpdateOperations(Account.class);
        setUnset(
            ops, "delegateConfiguration", DelegateConfiguration.builder().delegateVersions(asList(version)).build());
        wingsPersistence.update(globalAccountQuery, ops);
      }
    } catch (Exception e) {
      log.error("Unable to Publish local delegate version", e);
    }
  }

  public static void ensureSshDelegateCleanUp() {
    try {
      DockerClient docker = DefaultDockerClient.fromEnv().build();
      List<Container> containers = docker.listContainers();
      for (Container container : containers) {
        for (String name : container.names()) {
          if (name.equals("/ssh_delegate")) {
            docker.stopContainer(container.id(), 0);
            docker.removeContainer(container.id());
            log.info("Docker Container with name {} cleaned up", name);
            return;
          }
        }
      }
    } catch (DockerException | InterruptedException | DockerCertificateException e) {
      log.error("unable to cleanup docker container", e);
    }
  }

  public static boolean isMac() {
    String OS = System.getProperty("os.name").toLowerCase();
    return OS.indexOf("mac") >= 0;
  }
}
