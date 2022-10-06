# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

echo <+infra.variables.var1>
INSTANCE_OUTPUT_PATH='{"hosts":[{ "hostname": "instance1", "artifactBuildNo": "2021.07.10_app_2.war" }, { "hostname": "instance2", "artifactBuildNo": "2021.07.10_app_2.war" } ] }'
