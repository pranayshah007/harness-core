/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.nexus.service;

import static io.harness.rule.OwnerRule.SHIVAM;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.NexusRestClient;
import io.harness.nexus.NexusTwoClientImpl;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class NexusTwoClientImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.options().wireMockConfig().port(Options.DYNAMIC_PORT), false);
  @InjectMocks NexusTwoClientImpl nexusTwoService;
  @Mock NexusRestClient nexusRestClient;

  private static String url;
  private static String artifactRepoUrl;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    url = "http://localhost:" + wireMockRule.port();
    artifactRepoUrl = "http://localhost:999";
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositories() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(false)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories"))
            .willReturn(aResponse().withStatus(200).withBody("<repositories>\n"
                + "  <data>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/ken_hosted_repro</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/ken_hosted_repro</contentResourceURI>\n"
                + "      <id>ken_hosted_repro</id>\n"
                + "      <name>ken_hosted_repro</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>maven2</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/ken_hosted_repro/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/docker</contentResourceURI>\n"
                + "      <id>docker</id>\n"
                + "      <name>docker</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>maven2</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/docker/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "  </data>\n"
                + "</repositories>")));
    Map<String, String> response = nexusTwoService.getRepositories(nexusConfig, RepositoryFormat.maven.name());

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositoriesWithCredentials() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories"))
            .willReturn(aResponse().withStatus(200).withBody("<repositories>\n"
                + "  <data>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/ken_hosted_repro</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/ken_hosted_repro</contentResourceURI>\n"
                + "      <id>ken_hosted_repro</id>\n"
                + "      <name>ken_hosted_repro</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>maven2</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/ken_hosted_repro/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/docker</contentResourceURI>\n"
                + "      <id>docker</id>\n"
                + "      <name>docker</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>maven2</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/docker/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "  </data>\n"
                + "</repositories>")));
    Map<String, String> response = nexusTwoService.getRepositories(nexusConfig, RepositoryFormat.maven.name());

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositoriesException() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories"))
            .willReturn(aResponse().withStatus(404).withBody("<repositories>\n"
                + "  <data>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/ken_hosted_repro</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/ken_hosted_repro</contentResourceURI>\n"
                + "      <id>ken_hosted_repro</id>\n"
                + "      <name>ken_hosted_repro</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>maven2</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/ken_hosted_repro/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/docker</contentResourceURI>\n"
                + "      <id>docker</id>\n"
                + "      <name>docker</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>maven2</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/docker/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "  </data>\n"
                + "</repositories>")));
    assertThatThrownBy(() -> nexusTwoService.getRepositories(nexusConfig, RepositoryFormat.maven.name()))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositoriesNuget() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories"))
            .willReturn(aResponse().withStatus(200).withBody("<repositories>\n"
                + "  <data>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/ken_hosted_repro</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/ken_hosted_repro</contentResourceURI>\n"
                + "      <id>ken_hosted_repro</id>\n"
                + "      <name>ken_hosted_repro</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>nuget</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/ken_hosted_repro/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "    <repositories-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker</resourceURI>\n"
                + "      <contentResourceURI>https://nexus2.dev.harness.io/content/repositories/docker</contentResourceURI>\n"
                + "      <id>docker</id>\n"
                + "      <name>docker</name>\n"
                + "      <repoType>hosted</repoType>\n"
                + "      <repoPolicy>RELEASE</repoPolicy>\n"
                + "      <provider>maven2</provider>\n"
                + "      <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                + "      <format>nuget</format>\n"
                + "      <userManaged>true</userManaged>\n"
                + "      <exposed>true</exposed>\n"
                + "      <effectiveLocalStorageUrl>file:/opt/nexus2/sonatype-work/nexus/storage/docker/</effectiveLocalStorageUrl>\n"
                + "    </repositories-item>\n"
                + "  </data>\n"
                + "</repositories>")));
    Map<String, String> response = nexusTwoService.getRepositories(nexusConfig, RepositoryFormat.nuget.name());

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetVersion() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/index_content/test/docker/"))
            .willReturn(aResponse().withStatus(200).withBody("<indexBrowserTreeViewResponse>\n"
                + "  <data>\n"
                + "    <type>G</type>\n"
                + "    <leaf>false</leaf>\n"
                + "    <nodeName>docker</nodeName>\n"
                + "    <path>/test/docker/</path>\n"
                + "    <children>\n"
                + "      <indexBrowserTreeNode>\n"
                + "        <type>A</type>\n"
                + "        <leaf>false</leaf>\n"
                + "        <nodeName>docker</nodeName>\n"
                + "        <path>/test/docker/</path>\n"
                + "        <children>\n"
                + "          <indexBrowserTreeNode>\n"
                + "            <type>V</type>\n"
                + "            <leaf>false</leaf>\n"
                + "            <nodeName>1.0</nodeName>\n"
                + "            <path>/test/docker/1.0/</path>\n"
                + "            <children>\n"
                + "              <indexBrowserTreeNode>\n"
                + "                <type>artifact</type>\n"
                + "                <leaf>true</leaf>\n"
                + "                <nodeName>docker-1.0.zip</nodeName>\n"
                + "                <path>/test/docker/1.0/docker-1.0.zip</path>\n"
                + "                <groupId>test</groupId>\n"
                + "                <artifactId>docker</artifactId>\n"
                + "                <version>1.0</version>\n"
                + "                <repositoryId>docker</repositoryId>\n"
                + "                <locallyAvailable>false</locallyAvailable>\n"
                + "                <artifactTimestamp>0</artifactTimestamp>\n"
                + "                <extension>zip</extension>\n"
                + "                <packaging>war</packaging>\n"
                + "                <artifactUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=1.0&amp;p=war</artifactUri>\n"
                + "                <pomUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=1.0&amp;p=pom</pomUri>\n"
                + "              </indexBrowserTreeNode>\n"
                + "            </children>\n"
                + "            <groupId>test</groupId>\n"
                + "            <artifactId>docker</artifactId>\n"
                + "            <version>1.0</version>\n"
                + "            <repositoryId>docker</repositoryId>\n"
                + "            <locallyAvailable>false</locallyAvailable>\n"
                + "            <artifactTimestamp>0</artifactTimestamp>\n"
                + "          </indexBrowserTreeNode>\n"
                + "          <indexBrowserTreeNode>\n"
                + "            <type>V</type>\n"
                + "            <leaf>false</leaf>\n"
                + "            <nodeName>2.0</nodeName>\n"
                + "            <path>/test/docker/2.0/</path>\n"
                + "            <children>\n"
                + "              <indexBrowserTreeNode>\n"
                + "                <type>artifact</type>\n"
                + "                <leaf>true</leaf>\n"
                + "                <nodeName>docker-2.0.zip</nodeName>\n"
                + "                <path>/test/docker/2.0/docker-2.0.zip</path>\n"
                + "                <groupId>test</groupId>\n"
                + "                <artifactId>docker</artifactId>\n"
                + "                <version>2.0</version>\n"
                + "                <repositoryId>docker</repositoryId>\n"
                + "                <locallyAvailable>false</locallyAvailable>\n"
                + "                <artifactTimestamp>0</artifactTimestamp>\n"
                + "                <extension>zip</extension>\n"
                + "                <packaging>zip</packaging>\n"
                + "                <artifactUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=2.0&amp;p=zip</artifactUri>\n"
                + "                <pomUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=2.0&amp;p=pom</pomUri>\n"
                + "              </indexBrowserTreeNode>\n"
                + "            </children>\n"
                + "            <groupId>test</groupId>\n"
                + "            <artifactId>docker</artifactId>\n"
                + "            <version>2.0</version>\n"
                + "            <repositoryId>docker</repositoryId>\n"
                + "            <locallyAvailable>false</locallyAvailable>\n"
                + "            <artifactTimestamp>0</artifactTimestamp>\n"
                + "          </indexBrowserTreeNode>\n"
                + "        </children>\n"
                + "        <groupId>test</groupId>\n"
                + "        <artifactId>docker</artifactId>\n"
                + "        <repositoryId>docker</repositoryId>\n"
                + "        <locallyAvailable>false</locallyAvailable>\n"
                + "        <artifactTimestamp>0</artifactTimestamp>\n"
                + "      </indexBrowserTreeNode>\n"
                + "    </children>\n"
                + "    <repositoryId>docker</repositoryId>\n"
                + "    <locallyAvailable>false</locallyAvailable>\n"
                + "    <artifactTimestamp>0</artifactTimestamp>\n"
                + "  </data>\n"
                + "</indexBrowserTreeViewResponse>")));
    List<BuildDetailsInternal> response =
        nexusTwoService.getVersions(nexusConfig, "docker", "test", "docker", null, null);

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetVersionWithClassifier() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/index_content/test/docker/"))
            .willReturn(aResponse().withStatus(200).withBody("<indexBrowserTreeViewResponse>\n"
                + "  <data>\n"
                + "    <type>G</type>\n"
                + "    <leaf>false</leaf>\n"
                + "    <nodeName>docker</nodeName>\n"
                + "    <path>/test/docker/</path>\n"
                + "    <children>\n"
                + "      <indexBrowserTreeNode>\n"
                + "        <type>A</type>\n"
                + "        <leaf>false</leaf>\n"
                + "        <nodeName>docker</nodeName>\n"
                + "        <path>/test/docker/</path>\n"
                + "        <children>\n"
                + "          <indexBrowserTreeNode>\n"
                + "            <type>V</type>\n"
                + "            <leaf>false</leaf>\n"
                + "            <nodeName>1.0</nodeName>\n"
                + "            <path>/test/docker/1.0/</path>\n"
                + "            <children>\n"
                + "              <indexBrowserTreeNode>\n"
                + "                <type>artifact</type>\n"
                + "                <leaf>true</leaf>\n"
                + "                <nodeName>docker-1.0.zip</nodeName>\n"
                + "                <path>/test/docker/1.0/docker-1.0.zip</path>\n"
                + "                <groupId>test</groupId>\n"
                + "                <artifactId>docker</artifactId>\n"
                + "                <version>1.0</version>\n"
                + "                <repositoryId>docker</repositoryId>\n"
                + "                <locallyAvailable>false</locallyAvailable>\n"
                + "                <artifactTimestamp>0</artifactTimestamp>\n"
                + "                <extension>zip</extension>\n"
                + "                <packaging>war</packaging>\n"
                + "                <artifactUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=1.0&amp;p=war</artifactUri>\n"
                + "                <pomUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=1.0&amp;p=pom</pomUri>\n"
                + "              </indexBrowserTreeNode>\n"
                + "            </children>\n"
                + "            <groupId>test</groupId>\n"
                + "            <artifactId>docker</artifactId>\n"
                + "            <version>1.0</version>\n"
                + "            <repositoryId>docker</repositoryId>\n"
                + "            <locallyAvailable>false</locallyAvailable>\n"
                + "            <artifactTimestamp>0</artifactTimestamp>\n"
                + "          </indexBrowserTreeNode>\n"
                + "          <indexBrowserTreeNode>\n"
                + "            <type>V</type>\n"
                + "            <leaf>false</leaf>\n"
                + "            <nodeName>2.0</nodeName>\n"
                + "            <path>/test/docker/2.0/</path>\n"
                + "            <children>\n"
                + "              <indexBrowserTreeNode>\n"
                + "                <type>artifact</type>\n"
                + "                <leaf>true</leaf>\n"
                + "                <nodeName>docker-2.0.zip</nodeName>\n"
                + "                <path>/test/docker/2.0/docker-2.0.zip</path>\n"
                + "                <groupId>test</groupId>\n"
                + "                <artifactId>docker</artifactId>\n"
                + "                <version>2.0</version>\n"
                + "                <repositoryId>docker</repositoryId>\n"
                + "                <locallyAvailable>false</locallyAvailable>\n"
                + "                <artifactTimestamp>0</artifactTimestamp>\n"
                + "                <extension>zip</extension>\n"
                + "                <packaging>zip</packaging>\n"
                + "                <artifactUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=2.0&amp;p=zip</artifactUri>\n"
                + "                <pomUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=2.0&amp;p=pom</pomUri>\n"
                + "              </indexBrowserTreeNode>\n"
                + "            </children>\n"
                + "            <groupId>test</groupId>\n"
                + "            <artifactId>docker</artifactId>\n"
                + "            <version>2.0</version>\n"
                + "            <repositoryId>docker</repositoryId>\n"
                + "            <locallyAvailable>false</locallyAvailable>\n"
                + "            <artifactTimestamp>0</artifactTimestamp>\n"
                + "          </indexBrowserTreeNode>\n"
                + "        </children>\n"
                + "        <groupId>test</groupId>\n"
                + "        <artifactId>docker</artifactId>\n"
                + "        <repositoryId>docker</repositoryId>\n"
                + "        <locallyAvailable>false</locallyAvailable>\n"
                + "        <artifactTimestamp>0</artifactTimestamp>\n"
                + "      </indexBrowserTreeNode>\n"
                + "    </children>\n"
                + "    <repositoryId>docker</repositoryId>\n"
                + "    <locallyAvailable>false</locallyAvailable>\n"
                + "    <artifactTimestamp>0</artifactTimestamp>\n"
                + "  </data>\n"
                + "</indexBrowserTreeViewResponse>")));

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/content/test%2Fdocker%2F1.0%2F"))
            .willReturn(aResponse().withStatus(200).withBody("<content>\n"
                + "  <data>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.zip</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.zip</relativePath>\n"
                + "      <text>docker-1.0.zip</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:24.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>452508367</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.pom.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.pom.md5</relativePath>\n"
                + "      <text>docker-1.0.pom.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:23.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.zip.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.zip.md5</relativePath>\n"
                + "      <text>docker-1.0.zip.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:31.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.pom</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.pom</relativePath>\n"
                + "      <text>docker-1.0.pom</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:23.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>473</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.zip.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.zip.sha1</relativePath>\n"
                + "      <text>docker-1.0.zip.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:31.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.pom.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.pom.sha1</relativePath>\n"
                + "      <text>docker-1.0.pom.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:23.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "  </data>\n"
                + "</content>")));
    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/content/test%2Fdocker%2F2.0%2F"))
            .willReturn(aResponse().withStatus(200).withBody("<content>\n"
                + "  <data>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.zip.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.zip.md5</relativePath>\n"
                + "      <text>docker-2.0.zip.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 06:20:41.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.pom</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.pom</relativePath>\n"
                + "      <text>docker-2.0.pom</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 05:50:55.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>444</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.zip</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.zip</relativePath>\n"
                + "      <text>docker-2.0.zip</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 06:20:41.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>26280339</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.zip.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.zip.sha1</relativePath>\n"
                + "      <text>docker-2.0.zip.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 06:20:41.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.pom.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.pom.md5</relativePath>\n"
                + "      <text>docker-2.0.pom.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 05:50:55.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.pom.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.pom.sha1</relativePath>\n"
                + "      <text>docker-2.0.pom.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 05:50:55.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "  </data>\n"
                + "</content>")));
    List<BuildDetailsInternal> response =
        nexusTwoService.getVersions(nexusConfig, "docker", "test", "docker", "zip", null);

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetVersionWithClassifierException() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("2.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/index_content/test/docker/"))
            .willReturn(aResponse().withStatus(200).withBody("<indexBrowserTreeViewResponse>\n"
                + "  <data>\n"
                + "    <type>G</type>\n"
                + "    <leaf>false</leaf>\n"
                + "    <nodeName>docker</nodeName>\n"
                + "    <path>/test/docker/</path>\n"
                + "    <children>\n"
                + "      <indexBrowserTreeNode>\n"
                + "        <type>A</type>\n"
                + "        <leaf>false</leaf>\n"
                + "        <nodeName>docker</nodeName>\n"
                + "        <path>/test/docker/</path>\n"
                + "        <children>\n"
                + "          <indexBrowserTreeNode>\n"
                + "            <type>V</type>\n"
                + "            <leaf>false</leaf>\n"
                + "            <nodeName>1.0</nodeName>\n"
                + "            <path>/test/docker/1.0/</path>\n"
                + "            <children>\n"
                + "              <indexBrowserTreeNode>\n"
                + "                <type>artifact</type>\n"
                + "                <leaf>true</leaf>\n"
                + "                <nodeName>docker-1.0.zip</nodeName>\n"
                + "                <path>/test/docker/1.0/docker-1.0.zip</path>\n"
                + "                <groupId>test</groupId>\n"
                + "                <artifactId>docker</artifactId>\n"
                + "                <version>1.0</version>\n"
                + "                <repositoryId>docker</repositoryId>\n"
                + "                <locallyAvailable>false</locallyAvailable>\n"
                + "                <artifactTimestamp>0</artifactTimestamp>\n"
                + "                <extension>zip</extension>\n"
                + "                <packaging>war</packaging>\n"
                + "                <artifactUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=1.0&amp;p=war</artifactUri>\n"
                + "                <pomUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=1.0&amp;p=pom</pomUri>\n"
                + "              </indexBrowserTreeNode>\n"
                + "            </children>\n"
                + "            <groupId>test</groupId>\n"
                + "            <artifactId>docker</artifactId>\n"
                + "            <version>1.0</version>\n"
                + "            <repositoryId>docker</repositoryId>\n"
                + "            <locallyAvailable>false</locallyAvailable>\n"
                + "            <artifactTimestamp>0</artifactTimestamp>\n"
                + "          </indexBrowserTreeNode>\n"
                + "          <indexBrowserTreeNode>\n"
                + "            <type>V</type>\n"
                + "            <leaf>false</leaf>\n"
                + "            <nodeName>2.0</nodeName>\n"
                + "            <path>/test/docker/2.0/</path>\n"
                + "            <children>\n"
                + "              <indexBrowserTreeNode>\n"
                + "                <type>artifact</type>\n"
                + "                <leaf>true</leaf>\n"
                + "                <nodeName>docker-2.0.zip</nodeName>\n"
                + "                <path>/test/docker/2.0/docker-2.0.zip</path>\n"
                + "                <groupId>test</groupId>\n"
                + "                <artifactId>docker</artifactId>\n"
                + "                <version>2.0</version>\n"
                + "                <repositoryId>docker</repositoryId>\n"
                + "                <locallyAvailable>false</locallyAvailable>\n"
                + "                <artifactTimestamp>0</artifactTimestamp>\n"
                + "                <extension>zip</extension>\n"
                + "                <packaging>zip</packaging>\n"
                + "                <artifactUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=2.0&amp;p=zip</artifactUri>\n"
                + "                <pomUri>https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=docker&amp;g=test&amp;a=docker&amp;v=2.0&amp;p=pom</pomUri>\n"
                + "              </indexBrowserTreeNode>\n"
                + "            </children>\n"
                + "            <groupId>test</groupId>\n"
                + "            <artifactId>docker</artifactId>\n"
                + "            <version>2.0</version>\n"
                + "            <repositoryId>docker</repositoryId>\n"
                + "            <locallyAvailable>false</locallyAvailable>\n"
                + "            <artifactTimestamp>0</artifactTimestamp>\n"
                + "          </indexBrowserTreeNode>\n"
                + "        </children>\n"
                + "        <groupId>test</groupId>\n"
                + "        <artifactId>docker</artifactId>\n"
                + "        <repositoryId>docker</repositoryId>\n"
                + "        <locallyAvailable>false</locallyAvailable>\n"
                + "        <artifactTimestamp>0</artifactTimestamp>\n"
                + "      </indexBrowserTreeNode>\n"
                + "    </children>\n"
                + "    <repositoryId>docker</repositoryId>\n"
                + "    <locallyAvailable>false</locallyAvailable>\n"
                + "    <artifactTimestamp>0</artifactTimestamp>\n"
                + "  </data>\n"
                + "</indexBrowserTreeViewResponse>")));

    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/content/test%2Fdocker%2F1.0%2F"))
            .willReturn(aResponse().withStatus(200).withBody("<content>\n"
                + "  <data>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.zip</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.zip</relativePath>\n"
                + "      <text>docker-1.0.zip</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:24.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>452508367</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.pom.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.pom.md5</relativePath>\n"
                + "      <text>docker-1.0.pom.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:23.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.zip.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.zip.md5</relativePath>\n"
                + "      <text>docker-1.0.zip.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:31.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.pom</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.pom</relativePath>\n"
                + "      <text>docker-1.0.pom</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:23.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>473</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.zip.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.zip.sha1</relativePath>\n"
                + "      <text>docker-1.0.zip.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:31.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/1.0/docker-1.0.pom.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/1.0/docker-1.0.pom.sha1</relativePath>\n"
                + "      <text>docker-1.0.pom.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-07-07 06:48:23.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "  </data>\n"
                + "</content>")));
    wireMockRule.stubFor(
        get(urlEqualTo("/service/local/repositories/docker/content/test%2Fdocker%2F2.0%2F"))
            .willReturn(aResponse().withStatus(200).withBody("<content>\n"
                + "  <data>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.zip.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.zip.md5</relativePath>\n"
                + "      <text>docker-2.0.zip.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 06:20:41.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.pom</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.pom</relativePath>\n"
                + "      <text>docker-2.0.pom</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 05:50:55.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>444</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.zip</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.zip</relativePath>\n"
                + "      <text>docker-2.0.zip</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 06:20:41.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>26280339</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.zip.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.zip.sha1</relativePath>\n"
                + "      <text>docker-2.0.zip.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 06:20:41.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.pom.md5</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.pom.md5</relativePath>\n"
                + "      <text>docker-2.0.pom.md5</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 05:50:55.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>32</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "    <content-item>\n"
                + "      <resourceURI>https://nexus2.dev.harness.io/service/local/repositories/docker/content/test/docker/2.0/docker-2.0.pom.sha1</resourceURI>\n"
                + "      <relativePath>/test/docker/2.0/docker-2.0.pom.sha1</relativePath>\n"
                + "      <text>docker-2.0.pom.sha1</text>\n"
                + "      <leaf>true</leaf>\n"
                + "      <lastModified>2020-10-30 05:50:55.0 UTC</lastModified>\n"
                + "      <sizeOnDisk>40</sizeOnDisk>\n"
                + "    </content-item>\n"
                + "  </data>\n"
                + "</content>")));
    assertThatThrownBy(() -> nexusTwoService.getVersions(nexusConfig, "docker", "test", "docker", "zip", "war"))
        .isInstanceOf(HintException.class);
  }
}
