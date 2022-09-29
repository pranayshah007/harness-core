# Copyright (C) 2020 The Dagger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
load("@dagger//:workspace_defs.bzl", "dagger_rules")
dagger_rules()


java_library(
    name = "module",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
       "//:lombok",
       ":dagger",
       "//420-delegate-agent:module",
       "//867-polling-contracts:module",
       "//platform-service/modules/notification-service/delegate-tasks:module",
       "@maven//:org_apache_commons_commons_math3",
       "@maven//:org_apache_logging_log4j_log4j_api",
       "@maven//:org_apache_logging_log4j_log4j_to_slf4j",
       "@maven//:org_atmosphere_wasync",
       "@maven//:org_codehaus_janino_commons_compiler",
       "@maven//:org_codehaus_janino_janino",
       "@maven//:org_springframework_boot_spring_boot_loader",
],
)

java_binary(
name = "dagger-rick-test",
# srcs = glob(["*.java"]),
main_class = "io.harness.delegate.app.modules.testdagger.DelegateTestApp",
runtime_deps = [
    ":module",
],
)
