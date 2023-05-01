/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

public interface TasTestConstants {
  String MANIFEST_YAML = "  applications:\n"
      + "  - name : appName\n"
      + "    memory: 350M\n"
      + "    instances : ((instances))\n"
      + "    path: ((FILE_LOCATION))\n"
      + "    routes:\n"
      + "      - route: ((ROUTE_MAP))\n";

  String VARS_YAML = "instances: 1";
  String VARS_YAML_1 = "instances: 2";

  String MANIFEST_YAML_PROCESS = "applications:\n"
      + "- name: my-app\n"
      + "  memory: 512M\n"
      + "  instances: ((instance))\n"
      + "  path: ./my-app.jar\n"
      + "  buildpacks:\n"
      + "  - java_buildpack\n"
      + "  services:\n"
      + "  - my-service\n"
      + "  routes:\n"
      + "  - route: my-app.example.com\n"
      + "  - route: my-app-alias.example.com\n"
      + "  timeout: 120\n"
      + "  processes:\n"
      + "    web: java -jar my-app.jar --server.port=$PORT\n"
      + "    worker: java -jar my-app.jar --worker";
}
