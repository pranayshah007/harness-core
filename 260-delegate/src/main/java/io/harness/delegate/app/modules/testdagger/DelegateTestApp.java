/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.harness.delegate.app.modules.testdagger;

import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.nexus.service.NexusRegistryService;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateTestApp {
  public static void main(String[] args) {
    AComponent aComponent = DaggerAComponent.create();
    DockerRegistryService d = aComponent.regService();
    NexusRegistryService n = aComponent.getNexusRegSvc();
    ArtifactoryRegistryService arc = aComponent.getArtifactoryRegistryService();
    Map<Class<? extends Exception>, ExceptionHandler> m = aComponent.getExceptionHandlerMap();
    log.info("Here-- \nDockerRegistryService: {} \n"
            + "NexusRegistryService: {} \n"
            + "ArtifactoryRegistryService {} \n"
            + "ExceptionHandlerMap {} \n",
        d.toString(), n.toString(), arc.toString(), m);
  }
}
