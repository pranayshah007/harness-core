/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.pcf;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.pcf.CfInternalConfig;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public interface PcfTestConstants {
  String URL = "URL";
  char[] USER_NAME_DECRYPTED = "USER_NAME_DECRYPTED".toCharArray();
  String ORG = "ORG";
  String SPACE = "SPACE";
  String STOPPED = "STOPPED";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String RUNNING = "RUNNING";
  String APP_ID = "APP_ID";
  String ACTIVITY_ID = "ACTIVITY_ID";
  String REGISTRY_HOST_NAME = "REGISTRY_HOST_NAME";
  String DOCKER_URL = "registry.hub.docker.com/harness/todolist-sample";
  String ECR_URL = "448640225317.dkr.ecr.us-east-1.amazonaws.com/todolist:latest";
  String GCR_URL = "gcr.io/playground-243019/hello-app:v1";
  String ARIIFACTORY_URL = "harness-pcf.jfrog.io/hello-world";
  String RELEASE_NAME = "name"
      + "_pcfCommandHelperTest";
  String MANIFEST_YAML = "  applications:\n"
      + "  - name : ${APPLICATION_NAME}\n"
      + "    memory: 350M\n"
      + "    instances : ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    routes:\n"
      + "      - route: ${ROUTE_MAP}\n";
  String MANIFEST_YAML_DOCKER = "applications:\n"
      + "- name: ${APPLICATION_NAME}\n"
      + "  memory: 500M\n"
      + "  instances : ${INSTANCE_COUNT}\n"
      + "  random-route: true";
  String MANIFEST_YAML_LOCAL_EXTENDED = "---\n"
      + "applications:\n"
      + "- name: ${APPLICATION_NAME}\n"
      + "  memory: 350M\n"
      + "  instances: ${INSTANCE_COUNT}\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: ${FILE_LOCATION}\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";
  String MANIFEST_YAML_LOCAL_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: /root/app\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";
  String MANIFEST_YAML_LOCAL_WITH_TEMP_ROUTES_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: /root/app\n"
      + "  routes:\n"
      + "  - route: appTemp.harness.io\n"
      + "  - route: stageTemp.harness.io\n";
  String MANIFEST_YAML_RESOLVED_WITH_RANDOM_ROUTE = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: /root/app\n"
      + "  random-route: true\n";
  String MANIFEST_YAML_NO_ROUTE = "  applications:\n"
      + "  - name: ${APPLICATION_NAME}\n"
      + "    memory: 350M\n"
      + "    instances: ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    no-route: true\n";
  String MANIFEST_YAML_NO_ROUTE_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: /root/app\n"
      + "  no-route: true\n";
  String MANIFEST_YAML_RANDOM_ROUTE = "  applications:\n"
      + "  - name: ${APPLICATION_NAME}\n"
      + "    memory: 350M\n"
      + "    instances: ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    random-route: true\n";
  String MANIFEST_YAML_RANDOM_ROUTE_WITH_HOST = "  applications:\n"
      + "  - name: ${APPLICATION_NAME}\n"
      + "    memory: 350M\n"
      + "    instances: ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    random-route: true\n";
  String MANIFEST_YAML_RANDON_ROUTE_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: /root/app\n"
      + "  random-route: true\n";
  String MANIFEST_YAML_DOCKER_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 500M\n"
      + "  instances: 0\n"
      + "  docker:\n"
      + "    image: registry.hub.docker.com/harness/todolist-sample\n"
      + "    username: admin\n"
      + "  random-route: true\n";
  String MANIFEST_YAML_ECR_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 500M\n"
      + "  instances: 0\n"
      + "  docker:\n"
      + "    image: 448640225317.dkr.ecr.us-east-1.amazonaws.com/todolist:latest\n"
      + "    username: AKIAWQ5IKSASRV2RUSNP\n"
      + "  random-route: true\n";
  String MANIFEST_YAML_GCR_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 500M\n"
      + "  instances: 0\n"
      + "  docker:\n"
      + "    image: gcr.io/playground-243019/hello-app:v1\n"
      + "    username: _json_key\n"
      + "  random-route: true\n";
  String MANIFEST_YAML_ARTIFACTORY_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 500M\n"
      + "  instances: 0\n"
      + "  docker:\n"
      + "    image: harness-pcf.jfrog.io/hello-world\n"
      + "    username: admin\n"
      + "  random-route: true\n";
  String MANIFEST_YAML_EXTENDED_SUPPORT_REMOTE = "  applications:\n"
      + "  - name : anyName\n"
      + "    memory: 350M\n"
      + "    instances : 2\n"
      + "    buildpacks: \n"
      + "      - dotnet_core_buildpack"
      + "    services:\n"
      + "      - PCCTConfig"
      + "      - PCCTAutoScaler"
      + "    path: /users/location\n"
      + "    routes:\n"
      + "      - route: qa.harness.io\n";
  String MANIFEST_YAML_EXTENDED_SUPPORT_REMOTE_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpacks:\n"
      + "  - dotnet_core_buildpack    services: null\n"
      + "  - PCCTConfig      - PCCTAutoScaler    path: /users/location\n"
      + "  path: /root/app\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";

  String MANIFEST_YAML_PROCESSES = "applications:\n"
      + "- name: \"AppName\"\n"
      + "  memory: 1\n"
      + "  instances: 2\n"
      + "  path: /vars/foo\n"
      + "  processes:\n"
      + "  - type: web\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 3\n"
      + "  - type: worker\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 4\n"
      + "  - type: worker1\n"
      + "    memory: 150M\n"
      + "    disk_quota: 350M\n"
      + "    instances: 5";

  String MANIFEST_YAML_PROCESSES_ALL_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 1\n"
      + "  instances: 0\n"
      + "  path: /root/app\n"
      + "  env:\n"
      + "    HARNESS__STATUS__IDENTIFIER: STAGE\n"
      + "  processes:\n"
      + "  - type: web\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 0\n"
      + "  - type: worker\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 0\n"
      + "  - type: worker1\n"
      + "    memory: 150M\n"
      + "    disk_quota: 350M\n"
      + "    instances: 0\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";

  String MANIFEST_YAML_PROCESSES_WEB_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 1\n"
      + "  instances: 0\n"
      + "  path: /root/app\n"
      + "  env:\n"
      + "    HARNESS__STATUS__IDENTIFIER: STAGE\n"
      + "  processes:\n"
      + "  - type: web\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 0\n"
      + "  - type: worker\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 4\n"
      + "  - type: worker1\n"
      + "    memory: 150M\n"
      + "    disk_quota: 350M\n"
      + "    instances: 5\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";

  String MANIFEST_YAML_PROCESSES_WEB_RESOLVED_NON_BG = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 1\n"
      + "  instances: 0\n"
      + "  path: /root/app\n"
      + "  processes:\n"
      + "  - type: web\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 0\n"
      + "  - type: worker\n"
      + "    memory: 100M\n"
      + "    disk_quota: 300M\n"
      + "    instances: 4\n"
      + "  - type: worker1\n"
      + "    memory: 150M\n"
      + "    disk_quota: 350M\n"
      + "    instances: 5\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";
  static CfInternalConfig getPcfConfig() {
    return CfInternalConfig.builder().username(USER_NAME_DECRYPTED).endpointUrl(URL).password(new char[0]).build();
  }
}
