/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.nexus;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.INVALID_ARTIFACT_SERVER;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.VINICIUS;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.WingsException;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.NexusThreeClientImpl;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.utils.RepositoryFormat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(_960_API_SERVICES)
@OwnedBy(CDC)
public class NexusServiceTest extends WingsBaseTest {
  private static final String XML_RESPONSE = "<indexBrowserTreeViewResponse>\n"
      + "  <data>\n"
      + "    <type>G</type>\n"
      + "    <leaf>false</leaf>\n"
      + "    <nodeName>rest-client</nodeName>\n"
      + "    <path>/software/wings/nexus/rest-client/</path>\n"
      + "    <children>\n"
      + "      <indexBrowserTreeNode>\n"
      + "        <type>A</type>\n"
      + "        <leaf>false</leaf>\n"
      + "        <nodeName>rest-client</nodeName>\n"
      + "        <path>/software/wings/nexus/rest-client/</path>\n"
      + "        <children>\n"
      + "          <indexBrowserTreeNode>\n"
      + "            <type>V</type>\n"
      + "            <leaf>false</leaf>\n"
      + "            <nodeName>3.1.2</nodeName>\n"
      + "            <path>/software/wings/nexus/rest-client/3.1.2/</path>\n"
      + "            <children>\n"
      + "              <indexBrowserTreeNode>\n"
      + "                <type>artifact</type>\n"
      + "                <leaf>true</leaf>\n"
      + "                <nodeName>rest-client-3.1.2.pom</nodeName>\n"
      + "                <path>/software/wings/nexus/rest-client/3.1.2/rest-client-3.1.2.pom</path>\n"
      + "                <groupId>software.wings.nexus</groupId>\n"
      + "                <artifactId>rest-client</artifactId>\n"
      + "                <version>3.1.2</version>\n"
      + "                <repositoryId>releases</repositoryId>\n"
      + "                <locallyAvailable>false</locallyAvailable>\n"
      + "                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                <extension>pom</extension>\n"
      + "                <artifactUri></artifactUri>\n"
      + "                <pomUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=3.1.2&amp;p=pom</pomUri>\n"
      + "              </indexBrowserTreeNode>\n"
      + "              <indexBrowserTreeNode>\n"
      + "                <type>artifact</type>\n"
      + "                <leaf>true</leaf>\n"
      + "                <nodeName>rest-client-3.1.2-capsule.jar</nodeName>\n"
      + "                <path>/software/wings/nexus/rest-client/3.1.2/rest-client-3.1.2-capsule.jar</path>\n"
      + "                <groupId>software.wings.nexus</groupId>\n"
      + "                <artifactId>rest-client</artifactId>\n"
      + "                <version>3.1.2</version>\n"
      + "                <repositoryId>releases</repositoryId>\n"
      + "                <locallyAvailable>false</locallyAvailable>\n"
      + "                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                <classifier>capsule</classifier>\n"
      + "                <extension>jar</extension>\n"
      + "                <packaging>jar</packaging>\n"
      + "                <artifactUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=3.1.2&amp;p=jar</artifactUri>\n"
      + "                <pomUri></pomUri>\n"
      + "              </indexBrowserTreeNode>\n"
      + "            </children>\n"
      + "            <groupId>software.wings.nexus</groupId>\n"
      + "            <artifactId>rest-client</artifactId>\n"
      + "            <version>3.1.2</version>\n"
      + "            <repositoryId>releases</repositoryId>\n"
      + "            <locallyAvailable>false</locallyAvailable>\n"
      + "            <artifactTimestamp>0</artifactTimestamp>\n"
      + "          </indexBrowserTreeNode>\n"
      + "          <indexBrowserTreeNode>\n"
      + "            <type>V</type>\n"
      + "            <leaf>false</leaf>\n"
      + "            <nodeName>3.0</nodeName>\n"
      + "            <path>/software/wings/nexus/rest-client/3.0/</path>\n"
      + "            <children>\n"
      + "              <indexBrowserTreeNode>\n"
      + "                <type>artifact</type>\n"
      + "                <leaf>true</leaf>\n"
      + "                <nodeName>rest-client-3.0.jar</nodeName>\n"
      + "                <path>/software/wings/nexus/rest-client/3.0/rest-client-3.0.jar</path>\n"
      + "                <groupId>software.wings.nexus</groupId>\n"
      + "                <artifactId>rest-client</artifactId>\n"
      + "                <version>3.0</version>\n"
      + "                <repositoryId>releases</repositoryId>\n"
      + "                <locallyAvailable>false</locallyAvailable>\n"
      + "                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                <extension>jar</extension>\n"
      + "                <packaging>jar</packaging>\n"
      + "                <artifactUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=3.0&amp;p=jar</artifactUri>\n"
      + "                <pomUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=3.0&amp;p=pom</pomUri>\n"
      + "              </indexBrowserTreeNode>\n"
      + "            </children>\n"
      + "            <groupId>software.wings.nexus</groupId>\n"
      + "            <artifactId>rest-client</artifactId>\n"
      + "            <version>3.0</version>\n"
      + "            <repositoryId>releases</repositoryId>\n"
      + "            <locallyAvailable>false</locallyAvailable>\n"
      + "            <artifactTimestamp>0</artifactTimestamp>\n"
      + "          </indexBrowserTreeNode>\n"
      + "        </children>\n"
      + "        <groupId>software.wings.nexus</groupId>\n"
      + "        <artifactId>rest-client</artifactId>\n"
      + "        <repositoryId>releases</repositoryId>\n"
      + "        <locallyAvailable>false</locallyAvailable>\n"
      + "        <artifactTimestamp>0</artifactTimestamp>\n"
      + "      </indexBrowserTreeNode>\n"
      + "    </children>\n"
      + "    <repositoryId>releases</repositoryId>\n"
      + "    <locallyAvailable>false</locallyAvailable>\n"
      + "    <artifactTimestamp>0</artifactTimestamp>\n"
      + "  </data>\n"
      + "</indexBrowserTreeViewResponse>";

  private static final String XML_RESPONSE_INDEX_TREE_BROWSER = "<indexBrowserTreeViewResponse>\n"
      + "    <data>\n"
      + "        <type>G</type>\n"
      + "        <leaf>false</leaf>\n"
      + "        <nodeName>demo</nodeName>\n"
      + "        <path>/io/harness/test/demo/</path>\n"
      + "        <children>\n"
      + "            <indexBrowserTreeNode>\n"
      + "                <type>A</type>\n"
      + "                <leaf>false</leaf>\n"
      + "                <nodeName>demo</nodeName>\n"
      + "                <path>/io/harness/test/demo/</path>\n"
      + "                <children>\n"
      + "                    <indexBrowserTreeNode>\n"
      + "                        <type>V</type>\n"
      + "                        <leaf>false</leaf>\n"
      + "                        <nodeName>2.0</nodeName>\n"
      + "                        <path>/io/harness/test/demo/2.0/</path>\n"
      + "                        <children>\n"
      + "                            <indexBrowserTreeNode>\n"
      + "                                <type>artifact</type>\n"
      + "                                <leaf>true</leaf>\n"
      + "                                <nodeName>demo-2.0.tar</nodeName>\n"
      + "                                <path>/io/harness/test/demo/2.0/demo-2.0.tar</path>\n"
      + "                                <groupId>io.harness.test</groupId>\n"
      + "                                <artifactId>demo</artifactId>\n"
      + "                                <version>2.0</version>\n"
      + "                                <repositoryId>releases</repositoryId>\n"
      + "                                <locallyAvailable>false</locallyAvailable>\n"
      + "                                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                                <extension>tar</extension>\n"
      + "                                <packaging>jar</packaging>\n"
      + "                                <artifactUri>\n"
      + "http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=io.harness.test&amp;a=demo&amp;v=2.0&amp;p=jar\n"
      + "</artifactUri>\n"
      + "                                <pomUri>\n"
      + "http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=io.harness.test&amp;a=demo&amp;v=2.0&amp;p=pom\n"
      + "</pomUri>\n"
      + "                            </indexBrowserTreeNode>\n"
      + "                        </children>\n"
      + "                        <groupId>io.harness.test</groupId>\n"
      + "                        <artifactId>demo</artifactId>\n"
      + "                        <version>2.0</version>\n"
      + "                        <repositoryId>releases</repositoryId>\n"
      + "                        <locallyAvailable>false</locallyAvailable>\n"
      + "                        <artifactTimestamp>0</artifactTimestamp>\n"
      + "                    </indexBrowserTreeNode>\n"
      + "                    <indexBrowserTreeNode>\n"
      + "                        <type>V</type>\n"
      + "                        <leaf>false</leaf>\n"
      + "                        <nodeName>1.0</nodeName>\n"
      + "                        <path>/io/harness/test/demo/1.0/</path>\n"
      + "                        <children>\n"
      + "                            <indexBrowserTreeNode>\n"
      + "                                <type>artifact</type>\n"
      + "                                <leaf>true</leaf>\n"
      + "                                <nodeName>demo-1.0.tar</nodeName>\n"
      + "                                <path>/io/harness/test/demo/1.0/demo-1.0.tar</path>\n"
      + "                                <groupId>io.harness.test</groupId>\n"
      + "                                <artifactId>demo</artifactId>\n"
      + "                                <version>1.0</version>\n"
      + "                                <repositoryId>releases</repositoryId>\n"
      + "                                <locallyAvailable>false</locallyAvailable>\n"
      + "                                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                                <extension>tar</extension>\n"
      + "                                <packaging>jar</packaging>\n"
      + "                                <artifactUri>\n"
      + "http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=io.harness.test&amp;a=demo&amp;v=1.0&amp;p=jar\n"
      + "</artifactUri>\n"
      + "                                <pomUri>\n"
      + "http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=io.harness.test&amp;a=demo&amp;v=1.0&amp;p=pom\n"
      + "</pomUri>\n"
      + "                            </indexBrowserTreeNode>\n"
      + "                        </children>\n"
      + "                        <groupId>io.harness.test</groupId>\n"
      + "                        <artifactId>demo</artifactId>\n"
      + "                        <version>1.0</version>\n"
      + "                        <repositoryId>releases</repositoryId>\n"
      + "                        <locallyAvailable>false</locallyAvailable>\n"
      + "                        <artifactTimestamp>0</artifactTimestamp>\n"
      + "                    </indexBrowserTreeNode>\n"
      + "                </children>\n"
      + "                <groupId>io.harness.test</groupId>\n"
      + "                <artifactId>demo</artifactId>\n"
      + "                <repositoryId>releases</repositoryId>\n"
      + "                <locallyAvailable>false</locallyAvailable>\n"
      + "                <artifactTimestamp>0</artifactTimestamp>\n"
      + "            </indexBrowserTreeNode>\n"
      + "        </children>\n"
      + "        <repositoryId>releases</repositoryId>\n"
      + "        <locallyAvailable>false</locallyAvailable>\n"
      + "        <artifactTimestamp>0</artifactTimestamp>\n"
      + "    </data>\n"
      + "</indexBrowserTreeViewResponse>";

  private static final String XML_CONTENT_RESPONSE_2 = "<content>\n"
      + "    <data>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.pom.sha1\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.pom.sha1</relativePath>\n"
      + "            <text>demo-1.0.pom.sha1</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:02.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>40</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.jar\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.jar</relativePath>\n"
      + "            <text>demo-1.0.jar</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:03.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>1667</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0-binary.jar\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0-binary.jar</relativePath>\n"
      + "            <text>demo-1.0-binary.jar</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:03.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>1667</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.pom\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.pom</relativePath>\n"
      + "            <text>demo-1.0.pom</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:02.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>1402</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.tar.sha1\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.tar.sha1</relativePath>\n"
      + "            <text>demo-1.0.tar.sha1</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:51.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>40</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.pom.md5\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.pom.md5</relativePath>\n"
      + "            <text>demo-1.0.pom.md5</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:02.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>32</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.jar.md5\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.jar.md5</relativePath>\n"
      + "            <text>demo-1.0.jar.md5</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:03.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>32</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.tar.md5\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.tar.md5</relativePath>\n"
      + "            <text>demo-1.0.tar.md5</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:51.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>32</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.jar.sha1\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.jar.sha1</relativePath>\n"
      + "            <text>demo-1.0.jar.sha1</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:03.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>40</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/1.0/demo-1.0.tar\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/1.0/demo-1.0.tar</relativePath>\n"
      + "            <text>demo-1.0.tar</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-09 06:18:51.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>0</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "    </data>\n"
      + "</content>";

  private static final String XML_CONTENT_RESPONSE_1 = "<content>\n"
      + "    <data>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.tar.md5\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.tar.md5</relativePath>\n"
      + "            <text>demo-2.0.tar.md5</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:16:35.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>32</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.jar\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.jar</relativePath>\n"
      + "            <text>demo-2.0.jar</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:15:54.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>1652</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.pom\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.pom</relativePath>\n"
      + "            <text>demo-2.0.pom</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:15:54.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>1402</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.tar.sha1\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.tar.sha1</relativePath>\n"
      + "            <text>demo-2.0.tar.sha1</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:16:34.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>40</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.jar.sha1\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.jar.sha1</relativePath>\n"
      + "            <text>demo-2.0.jar.sha1</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:15:54.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>40</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.pom.sha1\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.pom.sha1</relativePath>\n"
      + "            <text>demo-2.0.pom.sha1</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:15:54.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>40</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.pom.md5\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.pom.md5</relativePath>\n"
      + "            <text>demo-2.0.pom.md5</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:15:54.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>32</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.jar.md5\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.jar.md5</relativePath>\n"
      + "            <text>demo-2.0.jar.md5</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:15:54.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>32</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "        <content-item>\n"
      + "            <resourceURI>\n"
      + "http://localhost:8081/nexus/service/local/repositories/releases/content/io/harness/test/demo/2.0/demo-2.0.tar\n"
      + "</resourceURI>\n"
      + "            <relativePath>/io/harness/test/demo/2.0/demo-2.0.tar</relativePath>\n"
      + "            <text>demo-2.0.tar</text>\n"
      + "            <leaf>true</leaf>\n"
      + "            <lastModified>2019-09-06 21:16:34.0 UTC</lastModified>\n"
      + "            <sizeOnDisk>0</sizeOnDisk>\n"
      + "        </content-item>\n"
      + "    </data>\n"
      + "</content>";

  private static final String XML_RESPONSE_INDEX_TREE_BROWSER_2 = "<indexBrowserTreeViewResponse>\n"
      + "    <data>\n"
      + "        <type>G</type>\n"
      + "        <leaf>false</leaf>\n"
      + "        <nodeName>4.0</nodeName>\n"
      + "        <path>/mygroup/todolist/4.0/</path>\n"
      + "        <children>\n"
      + "            <indexBrowserTreeNode>\n"
      + "                <type>A</type>\n"
      + "                <leaf>false</leaf>\n"
      + "                <nodeName>todolist</nodeName>\n"
      + "                <path>/mygroup/todolist/</path>\n"
      + "                <children>\n"
      + "                    <indexBrowserTreeNode>\n"
      + "                        <type>V</type>\n"
      + "                        <leaf>false</leaf>\n"
      + "                        <nodeName>4.0</nodeName>\n"
      + "                        <path>/mygroup/todolist/4.0/</path>\n"
      + "                        <children>\n"
      + "                            <indexBrowserTreeNode>\n"
      + "                                <type>artifact</type>\n"
      + "                                <leaf>true</leaf>\n"
      + "                                <nodeName>todolist-4.0.war</nodeName>\n"
      + "                                <path>/mygroup/todolist/4.0/todolist-4.0.war</path>\n"
      + "                                <groupId>mygroup</groupId>\n"
      + "                                <artifactId>todolist</artifactId>\n"
      + "                                <version>4.0</version>\n"
      + "                                <repositoryId>releases</repositoryId>\n"
      + "                                <locallyAvailable>false</locallyAvailable>\n"
      + "                                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                                <extension>war</extension>\n"
      + "                                <packaging>war</packaging>\n"
      + "                                <artifactUri>\n"
      + "https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=releases&amp;g=mygroup&amp;a=todolist&amp;v=4.0&amp;p=war\n"
      + "</artifactUri>\n"
      + "                                <pomUri>\n"
      + "https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=releases&amp;g=mygroup&amp;a=todolist&amp;v=4.0&amp;p=pom\n"
      + "</pomUri>\n"
      + "                            </indexBrowserTreeNode>\n"
      + "                            <indexBrowserTreeNode>\n"
      + "                                <type>artifact</type>\n"
      + "                                <leaf>true</leaf>\n"
      + "                                <nodeName>todolist-4.0-sources.zip</nodeName>\n"
      + "                                <path>/mygroup/todolist/4.0/todolist-4.0-sources.zip</path>\n"
      + "                                <groupId>mygroup</groupId>\n"
      + "                                <artifactId>todolist</artifactId>\n"
      + "                                <version>4.0</version>\n"
      + "                                <repositoryId>releases</repositoryId>\n"
      + "                                <locallyAvailable>false</locallyAvailable>\n"
      + "                                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                                <classifier>sources</classifier>\n"
      + "                                <extension>zip</extension>\n"
      + "                                <packaging>zip</packaging>\n"
      + "                                <artifactUri>\n"
      + "https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=releases&amp;g=mygroup&amp;a=todolist&amp;v=4.0&amp;p=zip\n"
      + "</artifactUri>\n"
      + "                                <pomUri/>\n"
      + "                            </indexBrowserTreeNode>\n"
      + "                            <indexBrowserTreeNode>\n"
      + "                                <type>artifact</type>\n"
      + "                                <leaf>true</leaf>\n"
      + "                                <nodeName>todolist-4.0.zip</nodeName>\n"
      + "                                <path>/mygroup/todolist/4.0/todolist-4.0.zip</path>\n"
      + "                                <groupId>mygroup</groupId>\n"
      + "                                <artifactId>todolist</artifactId>\n"
      + "                                <version>4.0</version>\n"
      + "                                <repositoryId>releases</repositoryId>\n"
      + "                                <locallyAvailable>false</locallyAvailable>\n"
      + "                                <artifactTimestamp>0</artifactTimestamp>\n"
      + "                                <extension>zip</extension>\n"
      + "                                <packaging>zip</packaging>\n"
      + "                                <artifactUri>\n"
      + "https://nexus2.dev.harness.io/service/local/artifact/maven/redirect?r=releases&amp;g=mygroup&amp;a=todolist&amp;v=4.0&amp;p=zip\n"
      + "</artifactUri>\n"
      + "                                <pomUri/>\n"
      + "                            </indexBrowserTreeNode>\n"
      + "                        </children>\n"
      + "                        <groupId>mygroup</groupId>\n"
      + "                        <artifactId>todolist</artifactId>\n"
      + "                        <version>4.0</version>\n"
      + "                        <repositoryId>releases</repositoryId>\n"
      + "                        <locallyAvailable>false</locallyAvailable>\n"
      + "                        <artifactTimestamp>0</artifactTimestamp>\n"
      + "                    </indexBrowserTreeNode>\n"
      + "                </children>\n"
      + "                <groupId>mygroup</groupId>\n"
      + "                <artifactId>todolist</artifactId>\n"
      + "                <repositoryId>releases</repositoryId>\n"
      + "                <locallyAvailable>false</locallyAvailable>\n"
      + "                <artifactTimestamp>0</artifactTimestamp>\n"
      + "            </indexBrowserTreeNode>\n"
      + "        </children>\n"
      + "        <repositoryId>releases</repositoryId>\n"
      + "        <locallyAvailable>false</locallyAvailable>\n"
      + "        <artifactTimestamp>0</artifactTimestamp>\n"
      + "    </data>\n"
      + "</indexBrowserTreeViewResponse>";
  /**
   * The Wire mock rule.
   */
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                          .usingFilesUnderClasspath("400-rest/src/test/resources")
                                                          .disableRequestJournal()
                                                          .port(0));
  @Rule
  public WireMockRule wireMockRule2 = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                           .usingFilesUnderClasspath("400-rest/src/test/resources")
                                                           .disableRequestJournal()
                                                           .port(0));
  @Rule
  public WireMockRule wireMockRule3 = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                           .usingFilesUnderClasspath("400-rest/src/test/resources")
                                                           .disableRequestJournal()
                                                           .port(0));

  private String DEFAULT_NEXUS_URL;

  @Inject @InjectMocks private NexusService nexusService;
  @Inject @InjectMocks private NexusClientImpl nexusClient;

  private NexusRequest nexusConfig;
  private NexusRequest nexusThreeConfig;

  @Inject @InjectMocks DelegateFileManager delegateFileManager;
  @Mock private ArtifactCollectionTaskHelper artifactCollectionTaskHelper;

  @Before
  public void setup() {
    DEFAULT_NEXUS_URL = String.format("http://localhost:%d/nexus/", wireMockRule.port());
    nexusConfig = NexusRequest.builder()
                      .nexusUrl(DEFAULT_NEXUS_URL)
                      .username("admin")
                      .password("wings123!".toCharArray())
                      .hasCredentials(true)
                      .build();
    nexusThreeConfig = NexusRequest.builder()
                           .nexusUrl(DEFAULT_NEXUS_URL)
                           .version("3.x")
                           .username("admin")
                           .password("wings123!".toCharArray())
                           .hasCredentials(true)
                           .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRepositories() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("<repositories>\n"
                        + "    <data>\n"
                        + "        <repositories-item>\n"
                        + "            <resourceURI>http://localhost:8081/nexus/service/local/repositories/snapshots</resourceURI>\n"
                        + "            <contentResourceURI>http://localhost:8081/nexus/content/repositories/snapshots</contentResourceURI>\n"
                        + "            <id>snapshots</id>\n"
                        + "            <name>Snapshots</name>\n"
                        + "            <repoType>hosted</repoType>\n"
                        + "            <repoPolicy>SNAPSHOT</repoPolicy>\n"
                        + "            <provider>maven2</provider>\n"
                        + "            <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>\n"
                        + "            <format>maven2</format>\n"
                        + "            <userManaged>true</userManaged>\n"
                        + "            <exposed>true</exposed>\n"
                        + "            <effectiveLocalStorageUrl>file:/usr/local/var/nexus/storage/snapshots/</effectiveLocalStorageUrl>\n"
                        + "        </repositories-item>"
                        + " </data>"
                        + "</repositories>")
                    .withHeader("Content-Type", "application/xml")));
    assertThat(nexusClient.getRepositories(nexusConfig, null)).hasSize(1).containsEntry("snapshots", "Snapshots");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/content/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(""
                        + "<content>\n"
                        + "  <data>\n"
                        + "    <content-item>\n"
                        + "      <resourceURI>http://localhost:8081/nexus/service/local/repositories/releases/content/archetype-catalog.xml</resourceURI>\n"
                        + "      <relativePath>/archetype-catalog.xml</relativePath>\n"
                        + "      <text>archetype-catalog.xml</text>\n"
                        + "      <leaf>true</leaf>\n"
                        + "      <lastModified>2017-04-02 04:20:37.830 UTC</lastModified>\n"
                        + "      <sizeOnDisk>25</sizeOnDisk>\n"
                        + "    </content-item>\n"
                        + "    <content-item>\n"
                        + "      <resourceURI>http://localhost:8081/nexus/service/local/repositories/releases/content/fakepath/</resourceURI>\n"
                        + "      <relativePath>/fakepath/</relativePath>\n"
                        + "      <text>fakepath</text>\n"
                        + "      <leaf>false</leaf>\n"
                        + "      <lastModified>2017-03-30 20:08:56.0 UTC</lastModified>\n"
                        + "      <sizeOnDisk>-1</sizeOnDisk>\n"
                        + "    </content-item>\n"
                        + "  </data>\n"
                        + "</content>")
                    .withHeader("Content-Type", "application/xml")));
    assertThat(nexusService.getArtifactPaths(nexusConfig, "releases")).hasSize(2).contains("/fakepath/");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactPathsByRepo() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/content/fakepath"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(""
                        + "<content>\n"
                        + "  <data>\n"
                        + "    <content-item>\n"
                        + "      <resourceURI>http://localhost:8081/nexus/service/local/repositories/releases/content/fakepath/nexus-client-core/</resourceURI>\n"
                        + "      <relativePath>/fakepath/nexus-client-core/</relativePath>\n"
                        + "      <text>nexus-client-core</text>\n"
                        + "      <leaf>true</leaf>\n"
                        + "      <lastModified>2017-04-02 04:20:37.830 UTC</lastModified>\n"
                        + "      <sizeOnDisk>25</sizeOnDisk>\n"
                        + "    </content-item>\n"
                        + "  </data>\n"
                        + "</content>")
                    .withHeader("Content-Type", "application/xml")));
    assertThat(nexusService.getArtifactPaths(nexusConfig, "releases", "fakepath"))
        .hasSize(1)
        .contains("/fakepath/nexus-client-core/");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactPathsByRepoStartsWithUrl() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/content/fakepath"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(""
                        + "<content>\n"
                        + "  <data>\n"
                        + "    <content-item>\n"
                        + "      <resourceURI>http://localhost:8081/nexus/service/local/repositories/releases/content/fakepath/nexus-client-core/</resourceURI>\n"
                        + "      <relativePath>/fakepath/nexus-client-core/</relativePath>\n"
                        + "      <text>nexus-client-core</text>\n"
                        + "      <leaf>true</leaf>\n"
                        + "      <lastModified>2017-04-02 04:20:37.830 UTC</lastModified>\n"
                        + "      <sizeOnDisk>25</sizeOnDisk>\n"
                        + "    </content-item>\n"
                        + "  </data>\n"
                        + "</content>")
                    .withHeader("Content-Type", "application/xml")));
    assertThat(nexusService.getArtifactPaths(nexusConfig, "releases", "/fakepath"))
        .hasSize(1)
        .contains("/fakepath/nexus-client-core/");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRepositoriesError() {
    wireMockRule.stubFor(get(urlEqualTo("/nexus/service/local/repositories"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
                                             .withHeader("Content-Type", "application/xml")));
    Map<String, String> repositories = nexusClient.getRepositories(nexusConfig, null);
    assertThat(repositories).isEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRepositoriesError404() {
    NexusRequest config = NexusRequest.builder()
                              .nexusUrl(String.format("http://localhost:%d/nexus2/", wireMockRule.port()))
                              .version("3.x")
                              .username("admin")
                              .password("wings123!".toCharArray())
                              .build();
    assertThatThrownBy(() -> nexusClient.isRunning(config))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Check if the Nexus URL & Nexus version are correct. Nexus URLs are different for different Nexus versions")
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .hasMessage("The Nexus URL or the version for the connector is incorrect")
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .hasMessage("INVALID_ARTIFACT_SERVER")
        .extracting("params")
        .hasFieldOrPropertyWithValue("message", "Invalid Nexus connector details");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void shouldGetRepositoriesError404_reverse() {
    NexusRequest config = NexusRequest.builder()
                              .nexusUrl(String.format("http://localhost:%d/nexus3/", wireMockRule.port()))
                              .version("2.x")
                              .username("admin")
                              .password("wings123!".toCharArray())
                              .build();
    assertThatThrownBy(() -> nexusClient.getRepositories(config, null))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Check if the Nexus URL & Nexus version are correct. Nexus URLs are different for different Nexus versions")
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .hasMessage("The Nexus URL or the version for the connector is incorrect")
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .hasMessage("INVALID_ARTIFACT_SERVER")
        .extracting("params")
        .hasFieldOrPropertyWithValue("message", "Invalid Nexus connector details");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerRepositoriesNexus2xError() {
    wireMockRule.stubFor(get(urlEqualTo("/nexus/service/local/repositories"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withFault(Fault.EMPTY_RESPONSE)
                                             .withHeader("Content-Type", "application/xml")));
    assertThatThrownBy(() -> nexusClient.getRepositories(nexusConfig, RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(WingsException.class)
        .hasMessageContaining(INVALID_ARTIFACT_SERVER.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetNugetRepositoriesNexus2x() {
    Map<String, String> repoMap = nexusClient.getRepositories(nexusConfig, RepositoryFormat.nuget.name());
    assertThat(repoMap.size()).isEqualTo(1);
    assertThat(repoMap).containsKey("MyNuGet");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetNPMRepositoriesNexus2x() {
    Map<String, String> repoMap = nexusClient.getRepositories(nexusConfig, RepositoryFormat.npm.name());
    assertThat(repoMap.size()).isEqualTo(1);
    assertThat(repoMap).containsKey("harness-npm");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetMavenRepositoriesNexus2x() {
    Map<String, String> repoMap = nexusClient.getRepositories(nexusConfig, RepositoryFormat.maven.name());
    assertThat(repoMap.size()).isEqualTo(1);
    assertThat(repoMap).containsKey("Todolist_Snapshots");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactPathsByRepoError() {
    wireMockRule.stubFor(get(urlEqualTo("/nexus/service/local/repositories/releases/content/"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withFault(Fault.EMPTY_RESPONSE)
                                             .withHeader("Content-Type", "application/xml")));

    assertThatThrownBy(() -> nexusService.getArtifactPaths(nexusConfig, "releases"))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(WingsException.class)
        .hasMessageContaining("INVALID_ARTIFACT_SERVER");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactPathsByRepoStartsWithUrlError() {
    wireMockRule.stubFor(get(urlEqualTo("/nexus/service/local/repositories/releases/content/fakepath"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
                                             .withHeader("Content-Type", "application/xml")));

    assertThatThrownBy(() -> nexusService.getArtifactPaths(nexusConfig, "releases", "fakepath"))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(WingsException.class)
        .hasMessageContaining("INVALID_ARTIFACT_SERVER");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGroupIdPaths() {
    wireMockRule.stubFor(get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<indexBrowserTreeViewResponse>\n"
                                                 + "  <data>\n"
                                                 + "    <type>G</type>\n"
                                                 + "    <leaf>false</leaf>\n"
                                                 + "    <nodeName>/</nodeName>\n"
                                                 + "    <path>/</path>\n"
                                                 + "    <children>\n"
                                                 + "      <indexBrowserTreeNode>\n"
                                                 + "        <type>G</type>\n"
                                                 + "        <leaf>false</leaf>\n"
                                                 + "        <nodeName>fakepath</nodeName>\n"
                                                 + "        <path>/fakepath/</path>\n"
                                                 + "        <repositoryId>releases</repositoryId>\n"
                                                 + "        <locallyAvailable>false</locallyAvailable>\n"
                                                 + "        <artifactTimestamp>0</artifactTimestamp>\n"
                                                 + "      </indexBrowserTreeNode>\n"
                                                 + "    </children>\n"
                                                 + "    <repositoryId>releases</repositoryId>\n"
                                                 + "    <locallyAvailable>false</locallyAvailable>\n"
                                                 + "    <artifactTimestamp>0</artifactTimestamp>\n"
                                                 + "  </data>\n"
                                                 + "</indexBrowserTreeViewResponse>")
                                             .withHeader("Content-Type", "application/xml")));

    wireMockRule.stubFor(get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/fakepath/"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("<indexBrowserTreeViewResponse>\n"
                                                 + "  <data>\n"
                                                 + "    <type>G</type>\n"
                                                 + "    <leaf>false</leaf>\n"
                                                 + "    <nodeName>nexus-client-core</nodeName>\n"
                                                 + "    <path>/fakepath/nexus-cleint-core</path>\n"
                                                 + "    <children>\n"
                                                 + "      <indexBrowserTreeNode>\n"
                                                 + "        <type>A</type>\n"
                                                 + "        <leaf>false</leaf>\n"
                                                 + "        <nodeName>nexus-client-core</nodeName>\n"
                                                 + "        <path>/fakepath/nexus-cleint-core</path>\n"
                                                 + "        <repositoryId>releases</repositoryId>\n"
                                                 + "        <locallyAvailable>false</locallyAvailable>\n"
                                                 + "        <artifactTimestamp>0</artifactTimestamp>\n"
                                                 + "      </indexBrowserTreeNode>\n"
                                                 + "    </children>\n"
                                                 + "    <repositoryId>releases</repositoryId>\n"
                                                 + "    <locallyAvailable>false</locallyAvailable>\n"
                                                 + "    <artifactTimestamp>0</artifactTimestamp>\n"
                                                 + "  </data>\n"
                                                 + "</indexBrowserTreeViewResponse>")
                                             .withHeader("Content-Type", "application/xml")));

    assertThat(nexusService.getGroupIdPaths(nexusConfig, "releases", null)).hasSize(1).contains("fakepath");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactNames() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/software/wings/nexus/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("<indexBrowserTreeViewResponse>\n"
                        + "  <data>\n"
                        + "    <type>G</type>\n"
                        + "    <leaf>false</leaf>\n"
                        + "    <nodeName>nexus</nodeName>\n"
                        + "    <path>/software/wings/nexus/</path>\n"
                        + "    <children>\n"
                        + "      <indexBrowserTreeNode>\n"
                        + "        <type>A</type>\n"
                        + "        <leaf>false</leaf>\n"
                        + "        <nodeName>rest-client</nodeName>\n"
                        + "        <path>/software/wings/nexus/rest-client/</path>\n"
                        + "        <children>\n"
                        + "          <indexBrowserTreeNode>\n"
                        + "            <type>V</type>\n"
                        + "            <leaf>false</leaf>\n"
                        + "            <nodeName>2.1.2</nodeName>\n"
                        + "            <path>/software/wings/nexus/rest-client/2.1.2/</path>\n"
                        + "            <children>\n"
                        + "              <indexBrowserTreeNode>\n"
                        + "                <type>artifact</type>\n"
                        + "                <leaf>true</leaf>\n"
                        + "                <nodeName>rest-client-2.1.2.pom</nodeName>\n"
                        + "                <path>/software/wings/nexus/rest-client/2.1.2/rest-client-2.1.2.pom</path>\n"
                        + "                <groupId>software.wings.nexus</groupId>\n"
                        + "                <artifactId>rest-client</artifactId>\n"
                        + "                <version>2.1.2</version>\n"
                        + "                <repositoryId>releases</repositoryId>\n"
                        + "                <locallyAvailable>false</locallyAvailable>\n"
                        + "                <artifactTimestamp>0</artifactTimestamp>\n"
                        + "                <extension>pom</extension>\n"
                        + "                <artifactUri></artifactUri>\n"
                        + "                <pomUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=2.1.2&amp;p=pom</pomUri>\n"
                        + "              </indexBrowserTreeNode>\n"
                        + "              <indexBrowserTreeNode>\n"
                        + "                <type>artifact</type>\n"
                        + "                <leaf>true</leaf>\n"
                        + "                <nodeName>rest-client-2.1.2-capsule.jar</nodeName>\n"
                        + "                <path>/software/wings/nexus/rest-client/2.1.2/rest-client-2.1.2-capsule.jar</path>\n"
                        + "                <groupId>software.wings.nexus</groupId>\n"
                        + "                <artifactId>rest-client</artifactId>\n"
                        + "                <version>2.1.2</version>\n"
                        + "                <repositoryId>releases</repositoryId>\n"
                        + "                <locallyAvailable>false</locallyAvailable>\n"
                        + "                <artifactTimestamp>0</artifactTimestamp>\n"
                        + "                <classifier>capsule</classifier>\n"
                        + "                <extension>jar</extension>\n"
                        + "                <packaging>jar</packaging>\n"
                        + "                <artifactUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=2.1.2&amp;p=jar</artifactUri>\n"
                        + "                <pomUri></pomUri>\n"
                        + "              </indexBrowserTreeNode>\n"
                        + "            </children>\n"
                        + "            <groupId>software.wings.nexus</groupId>\n"
                        + "            <artifactId>rest-client</artifactId>\n"
                        + "            <version>2.1.2</version>\n"
                        + "            <repositoryId>releases</repositoryId>\n"
                        + "            <locallyAvailable>false</locallyAvailable>\n"
                        + "            <artifactTimestamp>0</artifactTimestamp>\n"
                        + "          </indexBrowserTreeNode>\n"
                        + "        </children>\n"
                        + "        <groupId>software.wings.nexus</groupId>\n"
                        + "        <artifactId>rest-client</artifactId>\n"
                        + "        <repositoryId>releases</repositoryId>\n"
                        + "        <locallyAvailable>false</locallyAvailable>\n"
                        + "        <artifactTimestamp>0</artifactTimestamp>\n"
                        + "      </indexBrowserTreeNode>\n"
                        + "    </children>\n"
                        + "    <repositoryId>releases</repositoryId>\n"
                        + "    <locallyAvailable>false</locallyAvailable>\n"
                        + "    <artifactTimestamp>0</artifactTimestamp>\n"
                        + "  </data>\n"
                        + "</indexBrowserTreeViewResponse>")
                    .withHeader("Content-Type", "application/xml")));

    assertThat(nexusService.getArtifactNames(nexusConfig, "releases", "software.wings.nexus"))
        .hasSize(1)
        .contains("rest-client");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetVersions() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/software/wings/nexus/rest-client/"))
            .willReturn(
                aResponse().withStatus(200).withBody(XML_RESPONSE).withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersions(nexusConfig, "releases", "software.wings.nexus", "rest-client", null, null);
    assertThat(buildDetails)
        .hasSize(2)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(tuple("3.0", "3.0"), tuple("3.1.2", "3.1.2"));

    assertThat(buildDetails)
        .hasSize(2)
        .extracting(BuildDetails::getBuildUrl)
        .containsExactly("http://localhost:" + wireMockRule.port()
                + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.0&p=jar&e=jar",
            "http://localhost:" + wireMockRule.port()
                + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.1.2&p=jar&e=jar&c=capsule");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("rest-client-3.0.jar");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:" + wireMockRule.port()
            + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.0&p=jar&e=jar");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("rest-client-3.1.2-capsule.jar");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:" + wireMockRule.port()
            + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.1.2&p=jar&e=jar&c=capsule");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionsWithExtensionAndClassifier() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/software/wings/nexus/rest-client/"))
            .willReturn(
                aResponse().withStatus(200).withBody(XML_RESPONSE).withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersions(nexusConfig, "releases", "software.wings.nexus", "rest-client", "jar", "capsule");
    assertThat(buildDetails)
        .hasSize(2)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(tuple("3.0", "3.0"), tuple("3.1.2", "3.1.2"));

    assertThat(buildDetails)
        .hasSize(2)
        .extracting(BuildDetails::getBuildUrl)
        .contains("http://localhost:" + wireMockRule.port()
            + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.1.2&p=jar&e=jar&c=capsule");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("rest-client-3.1.2-capsule.jar");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:" + wireMockRule.port()
            + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.1.2&p=jar&e=jar&c=capsule");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionsWithExtensionNotProvidedAndClassifierProvided() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/software/wings/nexus/rest-client/"))
            .willReturn(
                aResponse().withStatus(200).withBody(XML_RESPONSE).withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersions(nexusConfig, "releases", "software.wings.nexus", "rest-client", "", "capsule");
    assertThat(buildDetails)
        .hasSize(2)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(tuple("3.0", "3.0"), tuple("3.1.2", "3.1.2"));

    assertThat(buildDetails)
        .hasSize(2)
        .extracting(BuildDetails::getBuildUrl)
        .contains("http://localhost:" + wireMockRule.port()
            + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.1.2&p=jar&c=capsule");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("rest-client-3.1.2-capsule.jar");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:" + wireMockRule.port()
            + "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.1.2&p=jar&c=capsule");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactMavenNexus2x() {
    setPomModelWireMock();

    // Return artifacts under version
    wireMockRule.stubFor(
        get(urlEqualTo(
                "/nexus/service/local/repositories/releases/index_content/software/wings/nexus/rest-client/3.0/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("<indexBrowserTreeViewResponse>\n"
                        + "  <data>\n"
                        + "    <type>G</type>\n"
                        + "    <leaf>false</leaf>\n"
                        + "    <nodeName>3.0</nodeName>\n"
                        + "    <path>/software/wings/nexus/rest-client/3.0/</path>\n"
                        + "    <children>\n"
                        + "      <indexBrowserTreeNode>\n"
                        + "        <type>A</type>\n"
                        + "        <leaf>false</leaf>\n"
                        + "        <nodeName>rest-client</nodeName>\n"
                        + "        <path>/software/wings/nexus/rest-client/</path>\n"
                        + "        <children>\n"
                        + "          <indexBrowserTreeNode>\n"
                        + "            <type>V</type>\n"
                        + "            <leaf>false</leaf>\n"
                        + "            <nodeName>3.0</nodeName>\n"
                        + "            <path>/software/wings/nexus/rest-client/3.0/</path>\n"
                        + "            <children>\n"
                        + "              <indexBrowserTreeNode>\n"
                        + "                <type>artifact</type>\n"
                        + "                <leaf>true</leaf>\n"
                        + "                <nodeName>rest-client-3.0.jar</nodeName>\n"
                        + "                <path>/software/wings/nexus/rest-client/3.0/rest-client-3.0.jar</path>\n"
                        + "                <groupId>software.wings.nexus</groupId>\n"
                        + "                <artifactId>rest-client</artifactId>\n"
                        + "                <version>3.0</version>\n"
                        + "                <repositoryId>releases</repositoryId>\n"
                        + "                <locallyAvailable>false</locallyAvailable>\n"
                        + "                <artifactTimestamp>0</artifactTimestamp>\n"
                        + "                <extension>jar</extension>\n"
                        + "                <packaging>jar</packaging>\n"
                        + "                <artifactUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=3.0&amp;p=jar</artifactUri>\n"
                        + "                <pomUri>http://localhost:8081/nexus/service/local/artifact/maven/redirect?r=releases&amp;g=software.wings.nexus&amp;a=rest-client&amp;v=3.0&amp;p=pom</pomUri>\n"
                        + "              </indexBrowserTreeNode>\n"
                        + "            </children>\n"
                        + "            <groupId>software.wings.nexus</groupId>\n"
                        + "            <artifactId>rest-client</artifactId>\n"
                        + "            <version>3.0</version>\n"
                        + "            <repositoryId>releases</repositoryId>\n"
                        + "            <locallyAvailable>false</locallyAvailable>\n"
                        + "            <artifactTimestamp>0</artifactTimestamp>\n"
                        + "          </indexBrowserTreeNode>\n"
                        + "        </children>\n"
                        + "        <groupId>software.wings.nexus</groupId>\n"
                        + "        <artifactId>rest-client</artifactId>\n"
                        + "        <repositoryId>releases</repositoryId>\n"
                        + "        <locallyAvailable>false</locallyAvailable>\n"
                        + "        <artifactTimestamp>0</artifactTimestamp>\n"
                        + "      </indexBrowserTreeNode>\n"
                        + "    </children>\n"
                        + "    <repositoryId>releases</repositoryId>\n"
                        + "    <locallyAvailable>false</locallyAvailable>\n"
                        + "    <artifactTimestamp>0</artifactTimestamp>\n"
                        + "  </data>\n"
                        + "</indexBrowserTreeViewResponse>")
                    .withHeader("Content-Type", "application/xml")));

    wireMockRule.stubFor(get(
        urlEqualTo(
            "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.0&p=jar&e=jar"))
                             .willReturn(aResponse().withBody(new byte[] {1, 2, 3, 4})));
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .repositoryName("releases")
                                                            .groupId("software.wings.nexus")
                                                            .artifactName("rest-client")
                                                            .build();
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ArtifactMetadataKeys.buildNo, "3.0");
    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    DelegateFile delegateFile = DelegateFile.Builder.aDelegateFile().withFileId("FILE_ID").build();
    when(delegateFileManager.upload(any(), any())).thenReturn(delegateFile);
    nexusService.downloadArtifacts(
        nexusConfig, artifactStreamAttributes, artifactMetadata, null, null, null, listNotifyResponseData);
    assertThat(ArtifactFile.fromDTO(listNotifyResponseData.getData().get(0)).getFileUuid()).isEqualTo("FILE_ID");
    assertThat(ArtifactFile.fromDTO(listNotifyResponseData.getData().get(0)).getName())
        .isEqualTo("rest-client-3.0.jar");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetLatestVersion() {
    setPomModelWireMock();
    BuildDetails buildDetails =
        nexusService.getLatestVersion(nexusConfig, "releases", "software.wings.nexus", "rest-client");
    assertThat(buildDetails).extracting(BuildDetails::getNumber).isEqualTo("3.0");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerRepositories() {
    assertThat(nexusClient.getRepositories(nexusThreeConfig, RepositoryFormat.docker.name()))
        .hasSize(3)
        .containsEntry("docker-group", "docker-group");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDockerImages() {
    assertThat(nexusService.getGroupIdPaths(nexusThreeConfig, "docker-group", RepositoryFormat.docker.name()))
        .hasSize(1)
        .contains("wingsplugins/todolist");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDockerTags() {
    assertThat(nexusService.getBuilds(nexusThreeConfig,
                   ArtifactStreamAttributes.builder()
                       .artifactStreamType(ArtifactStreamType.NEXUS.name())
                       .metadataOnly(true)
                       .jobName("docker-group")
                       .imageName("wingsplugins/todolist")
                       .nexusDockerPort("5000")
                       .repositoryType("docker")
                       .build(),
                   10))
        .hasSize(3)
        .extracting(BuildDetails::getNumber)
        .contains("latest");
  }

  private void setPomModelWireMock() {
    // First return the POM Model
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/artifact/maven?r=releases&g=software.wings.nexus&a=rest-client&v=LATEST"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("<project>\n"
                                + "  <modelVersion>4.0.0</modelVersion>\n"
                                + "  <groupId>software.wings.nexus</groupId>\n"
                                + "  <artifactId>rest-client</artifactId>\n"
                                + "  <version>3.0</version>\n"
                                + "  <packaging>jar</packaging>\n"
                                + "  <description>POM was created by Sonatype Nexus</description>\n"
                                + "</project>")
                            .withHeader("Content-Type", "application/xml")));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExistsVersionWithExtensionNexus2x() {
    mockResponsesNexus2xForExistVersions();
    assertThat(nexusService.existsVersion(nexusConfig, "releases", "io.harness.test", "demo", "jar", null)).isTrue();
  }

  @Test(expected = ArtifactServerException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testNoVersionsFoundWithInvalidClassifierNexus2x() {
    mockResponsesNexus2xForExistVersions();
    nexusService.existsVersion(nexusConfig, "releases", "io.harness.test", "demo", null, "sources");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testVersionExistsWithValidClassifierExtensionNexus2x() {
    mockResponsesNexus2xForExistVersions();
    assertThat(nexusService.existsVersion(nexusConfig, "releases", "io.harness.test", "demo", "jar", "binary"))
        .isTrue();
  }

  private void mockResponsesNexus2xForExistVersions() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/io/harness/test/demo/"))
            .willReturn(aResponse().withStatus(200).withBody(XML_RESPONSE_INDEX_TREE_BROWSER)));

    wireMockRule.stubFor(
        get(urlMatching("/nexus/service/local/repositories/releases/content/io%2Fharness%2Ftest%2Fdemo%2F1.0%2F"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(XML_CONTENT_RESPONSE_2)
                            .withHeader("Content-Type", "application/xml")));
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/content/io%2Fharness%2Ftest%2Fdemo%2F2.0%2F"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(XML_CONTENT_RESPONSE_1)
                            .withHeader("Content-Type", "application/xml")));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExistsVersionWithExtensionNexus3x() {
    assertThat(nexusService.existsVersion(nexusThreeConfig, "maven-releases", "mygroup", "myartifact", "jar", "binary"))
        .isTrue();
  }

  @Test(expected = ArtifactServerException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testNoVersionsFoundWithInvalidClassifierNexus3x() {
    nexusService.existsVersion(nexusThreeConfig, "maven-releases", "mygroup", "myartifact", null, "source");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPackageNamesForNPMNexus3x() {
    assertThat(nexusService.getGroupIdPaths(nexusThreeConfig, "harness-npm", RepositoryFormat.npm.name()))
        .hasSize(1)
        .contains("npm-app1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPackageNamesForNugetNexus3x() {
    assertThat(nexusService.getGroupIdPaths(nexusThreeConfig, "nuget-group", RepositoryFormat.nuget.name()))
        .hasSize(4)
        .contains("AdamsLair.Duality.Samples.BasicMenu", "AdamsLair.Duality.Samples.InputHandling",
            "NuGet.Package.Sample", "NuGet.Sample.Package");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldGetPackageNamesForRawNexus3x() {
    assertThat(nexusService.getGroupIdPaths(nexusThreeConfig, "raw-group", RepositoryFormat.raw.name()))
        .hasSize(2)
        .contains("Raw.Sample.Package", "Raw.Sample.Package1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetGroupIdsForMavenNexus3x() {
    assertThat(nexusService.getGroupIdPaths(nexusThreeConfig, "maven-releases", RepositoryFormat.maven.name()))
        .hasSize(1)
        .contains("mygroup");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetArtifactNamesForMavenNexus3x() {
    assertThat(
        nexusService.getArtifactNames(nexusThreeConfig, "maven-releases", "mygroup", RepositoryFormat.maven.name()))
        .hasSize(1)
        .contains("myartifact");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionsForMavenNexus3x() {
    List<BuildDetails> buildDetails =
        nexusService.getVersions(nexusThreeConfig, "maven-releases", "mygroup", "myartifact", null, null);
    assertThat(buildDetails).hasSize(2).extracting(BuildDetails::getNumber).contains("1.0", "1.8");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("myartifact-1.0.war");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionsForMavenNexus3xForGroupRepos() {
    List<BuildDetails> buildDetails =
        nexusService.getVersions(nexusThreeConfig, "maven-internal-group", "mygroup", "myartifact", null, null);
    assertThat(buildDetails).hasSize(1);
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(
            "http://localhost:8881/nexus/repository/maven-internal-group/mygroup/myartifact/1.0/myartifact-1.0.war");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionsForMavenNexus3xForGroupRepos2() {
    // This is to test if group repo name is a substring of member repo name
    List<BuildDetails> buildDetails =
        nexusService.getVersions(nexusThreeConfig, "maven-internal", "com.mygroup", "myartifact", null, null);
    assertThat(buildDetails).hasSize(1);
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(
            "http://localhost:8881/nexus/repository/maven-internal/com/mygroup/myartifact/1.0/myartifact-1.0.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionsForNPMNexus3x() {
    List<BuildDetails> buildDetails = nexusService.getVersions(
        RepositoryFormat.npm.name(), nexusThreeConfig, "harness-npm", "npm-app1", new HashSet<>());
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("1.0.0");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("npm-app1-1.0.0.tgz");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/harness-npm/npm-app1/-/npm-app1-1.0.0.tgz");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionsForNPMNexus3xForGroupRepos() {
    List<BuildDetails> buildDetails = nexusService.getVersions(
        RepositoryFormat.npm.name(), nexusThreeConfig, "harness-npm-group", "npm-app1", new HashSet<>());
    assertThat(buildDetails).hasSize(1);
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("npm-app1-1.0.0.tgz");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/harness-npm-group/npm-app1/-/npm-app1-1.0.0.tgz");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionsForNugetNexus3x() {
    List<BuildDetails> buildDetails = nexusService.getVersions(
        RepositoryFormat.nuget.name(), nexusThreeConfig, "nuget-group", "NuGet.Sample.Package", new HashSet<>());
    assertThat(buildDetails).hasSize(2).extracting(BuildDetails::getNumber).contains("1.0.0.0", "1.0.0.18279");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("NuGet.Sample.Package-1.0.0.0.nupkg");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/nuget-group/NuGet.Sample.Package/1.0.0.0");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("NuGet.Sample.Package-1.0.0.18279.nupkg");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/nuget-group/NuGet.Sample.Package/1.0.0.18279");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldGetNamesForRawNexus3x() {
    List<BuildDetails> buildDetails =
        nexusService.getPackageNames(nexusThreeConfig, "raw-group", "Raw.Sample.Package", RepositoryFormat.raw.name());
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("Raw.Sample.Package");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("Raw.Sample.Package");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/raw-group/Raw.Sample.Package");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionsForNugetNexus3xForGroupRepos() {
    List<BuildDetails> buildDetails = nexusService.getVersions(RepositoryFormat.nuget.name(), nexusThreeConfig,
        "nuget-hosted-group-repo", "NuGet.Sample.Package", new HashSet<>());
    assertThat(buildDetails).hasSize(2).extracting(BuildDetails::getNumber).contains("1.0.0.0", "1.0.0.18279");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("NuGet.Sample.Package-1.0.0.0.nupkg");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/nuget-hosted-group-repo/NuGet.Sample.Package/1.0.0.0");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldGetNamesForRawNexus3xForGroupRepos() {
    List<BuildDetails> buildDetails = nexusService.getPackageNames(
        nexusThreeConfig, "raw-hosted-group-repo", "Raw.Sample.Package", RepositoryFormat.raw.name());
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("Raw.Sample.Package");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("Raw.Sample.Package");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("http://localhost:8881/nexus/repository/raw-hosted-group-repo/Raw.Sample.Package");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPackageNamesForNPMNexus2x() {
    assertThat(nexusService.getGroupIdPaths(nexusConfig, "npmjs", RepositoryFormat.npm.name()))
        .hasSize(5)
        .contains("chalk");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPackageNamesForNugetNexus2x() {
    assertThat(nexusService.getGroupIdPaths(nexusConfig, "MyNuGet", RepositoryFormat.nuget.name()))
        .hasSize(1)
        .contains("NuGet.Sample.Package");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionsForNugetNexus2x() {
    List<BuildDetails> buildDetails = nexusService.getVersions(
        RepositoryFormat.nuget.name(), nexusConfig, "MyNuGet", "NuGet.Sample.Package", new HashSet<>());
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("1.0.0.18279");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("NuGet.Sample.Package-1.0.0.18279.nupkg");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(
            "http://localhost:8881/nexus/service/local/repositories/MyNuGet/content/NuGet.Sample.Package/1.0.0.18279/NuGet.Sample.Package-1.0.0.18279.nupkg");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionsForNPMNexus2x() {
    List<BuildDetails> buildDetails =
        nexusService.getVersions(RepositoryFormat.npm.name(), nexusConfig, "npmjs", "abbrev", new HashSet<>());
    assertThat(buildDetails).hasSize(3).extracting(BuildDetails::getNumber).contains("1.0.3", "1.0.4", "1.0.5");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("abbrev-1.0.3.tgz");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("https://nexus2.harness.io/content/repositories/npmjs/abbrev/-/abbrev-1.0.3.tgz");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("abbrev-1.0.4.tgz");
    assertThat(buildDetails.get(1).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("https://nexus2.harness.io/content/repositories/npmjs/abbrev/-/abbrev-1.0.4.tgz");
    assertThat(buildDetails.get(2).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("abbrev-1.0.5.tgz");
    assertThat(buildDetails.get(2).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("https://nexus2.harness.io/content/repositories/npmjs/abbrev/-/abbrev-1.0.5.tgz");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactNPMNexus3() {
    ArtifactStreamAttributes artifactStreamAttributes =
        ArtifactStreamAttributes.builder().repositoryFormat(RepositoryFormat.npm.name()).build();
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ArtifactMetadataKeys.repositoryName, "harness-npm");
    artifactMetadata.put(ArtifactMetadataKeys.nexusPackageName, "npm-app1");
    artifactMetadata.put(ArtifactMetadataKeys.buildNo, "1.0.0");

    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    nexusService.downloadArtifacts(
        nexusThreeConfig, artifactStreamAttributes, artifactMetadata, null, null, null, listNotifyResponseData);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactMavenNexus3() {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .repositoryFormat(RepositoryFormat.maven.name())
                                                            .jobName("maven-releases")
                                                            .groupId("mygroup")
                                                            .artifactName("myartifact")
                                                            .build();
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ArtifactMetadataKeys.buildNo, "1.0");

    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    nexusService.downloadArtifacts(
        nexusThreeConfig, artifactStreamAttributes, artifactMetadata, null, null, null, listNotifyResponseData);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionDownloadArtifactNPMNexus2() {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .repositoryFormat(RepositoryFormat.npm.name())
                                                            .repositoryName("npmjs")
                                                            .build();
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(
        ArtifactMetadataKeys.url, "http://localhost:8881/nexus/content/repositories/npmjs/abbrev/-/abbrev-1.0.3.tgz");
    artifactMetadata.put(ArtifactMetadataKeys.buildNo, "1.0.3");

    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    nexusService.downloadArtifacts(
        nexusConfig, artifactStreamAttributes, artifactMetadata, null, null, null, listNotifyResponseData);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldFileNotFoundDownloadArtifactNugetNexus2() {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .repositoryFormat(RepositoryFormat.nuget.name())
                                                            .repositoryName("MyNuGet")
                                                            .nexusPackageName("NuGet.Sample.Package")
                                                            .build();
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ArtifactMetadataKeys.buildNo, "1.0.0.18279");

    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    nexusService.downloadArtifacts(
        nexusConfig, artifactStreamAttributes, artifactMetadata, null, null, null, listNotifyResponseData);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testNonMatchingServerAndVersionWithAuthentication() {
    NexusRequest config = NexusRequest.builder()
                              .nexusUrl(String.format("http://localhost:%d/", wireMockRule2.port()))
                              .version("3.x")
                              .username("admin")
                              .password("wings123!".toCharArray())
                              .build();
    assertThatThrownBy(() -> nexusClient.isRunning(config))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidConnection() throws IOException {
    assertThat(nexusClient.isRunning(nexusThreeConfig)).isTrue();
    assertThat(nexusClient.isRunning(nexusConfig)).isTrue();

    NexusThreeClientImpl nexusThreeService = Mockito.mock(NexusThreeClientImpl.class);
    Reflect.on(nexusClient).set("nexusThreeService", nexusThreeService);

    when(nexusThreeService.isServerValid(any())).thenThrow(new RuntimeException("Some uncaught exception"));
    assertThat(nexusClient.isRunning(nexusThreeConfig)).isTrue();

    nexusThreeService = Mockito.mock(NexusThreeClientImpl.class);
    Reflect.on(nexusClient).set("nexusThreeService", nexusThreeService);

    // Note: Throw any WingsException. This exception may not actually be thrown here.
    when(nexusThreeService.isServerValid(any())).thenThrow(new UnsupportedOperationException("Invalid server"));
    assertThat(nexusClient.isRunning(nexusThreeConfig)).isTrue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testNonMatchingServerAndVersionWithoutAuthentication() {
    NexusRequest config = NexusRequest.builder()
                              .nexusUrl(String.format("http://localhost:%d/", wireMockRule2.port()))
                              .version("3.x")
                              .build();

    assertThatThrownBy(() -> nexusClient.isRunning(config))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testInvalidCredentials() {
    NexusRequest config = NexusRequest.builder()
                              .nexusUrl(String.format("http://localhost:%d/", wireMockRule3.port()))
                              .version("3.x")
                              .username("admin")
                              .password("wings123!".toCharArray())
                              .build();

    wireMockRule3.stubFor(get(urlEqualTo("/service/rest/v1/repositories")).willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> nexusClient.isRunning(config))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(WingsException.class)
        .hasMessageContaining("INVALID_ARTIFACT_SERVER");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getArtifactFileSizeForNexus2xMavenArtifact() {
    wireMockRule.stubFor(get(urlEqualTo("/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war"))
                             .willReturn(aResponse().withBody(new byte[] {1, 2, 3, 4, 5})));
    long size = nexusService.getFileSize(nexusConfig, "myartifact-1.0.war",
        "http://localhost:" + wireMockRule.port()
            + "/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war");
    assertThat(size).isEqualTo(5);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getArtifactFileSizeForNexus3xMavenArtifact() {
    wireMockRule3.stubFor(get(urlEqualTo("/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war"))
                              .willReturn(aResponse().withBody(new byte[] {1, 2, 3, 4})));
    long size = nexusService.getFileSize(nexusThreeConfig, "myartifact-1.0.war",
        String.format("http://localhost:%d/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war",
            wireMockRule3.port()));
    assertThat(size).isEqualTo(4);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadNexus2xMavenArtifactByUrl() throws IOException {
    String content = "file content";
    String fileName = "rest-client-3.0.jar";
    wireMockRule.stubFor(get(
        urlEqualTo(
            "/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.0&p=jar&e=jar&c=sources"))
                             .willReturn(aResponse().withBody(content.getBytes())));
    Pair<String, InputStream> pair = nexusService.downloadArtifactByUrl(nexusConfig, fileName,
        String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=software.wings.nexus&a=rest-client&v=3.0&p=jar&e=jar&c=sources",
            wireMockRule.port()));
    assertThat(pair).isNotNull();
    assertThat(pair.getKey()).isEqualTo(fileName);
    String text = IOUtils.toString(pair.getRight(), StandardCharsets.UTF_8.name());
    assertThat(text).isEqualTo(content);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadNexus3xMavenArtifactByUrl() throws IOException {
    String content = "file content";
    String fileName = "myartifact-1.0.jar";
    wireMockRule3.stubFor(get(urlEqualTo("/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.jar"))
                              .willReturn(aResponse().withBody(content.getBytes())));
    Pair<String, InputStream> pair = nexusService.downloadArtifactByUrl(nexusThreeConfig, fileName,
        String.format("http://localhost:%d/nexus/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.jar",
            wireMockRule3.port()));
    assertThat(pair).isNotNull();
    assertThat(pair.getKey()).isEqualTo(fileName);
    String text = IOUtils.toString(pair.getRight(), StandardCharsets.UTF_8.name());
    assertThat(text).isEqualTo(content);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadNexus3xNPMArtifact() {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .repositoryFormat(RepositoryFormat.npm.name())
                                                            .repositoryName("npm-test")
                                                            .nexusPackageName("npm-app1")
                                                            .build();
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ArtifactMetadataKeys.buildNo, "1.0.0");
    artifactMetadata.put(ArtifactMetadataKeys.nexusPackageName, "npm-app1");
    artifactMetadata.put(ArtifactMetadataKeys.repositoryName, "npm-test");
    String content = "file content";
    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    DelegateFile delegateFile = DelegateFile.Builder.aDelegateFile().withFileId("npm-app1-1.0.0.tgz").build();
    when(delegateFileManager.upload(any(), any())).thenReturn(delegateFile);
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/rest/v1/search/assets?repository=npm-test&name=npm-app1&version=1.0.0"))
            .willReturn(
                aResponse()
                    .withBody("{\n"
                        + "  \"items\": [\n"
                        + "    {\n"
                        + "      \"downloadUrl\": \"http://localhost:" + wireMockRule3.port()
                        + "/nexus/repository/npm-test/npm-app1/-/npm-app1-1.0.0.tgz\",\n"
                        + "      \"path\": \"npm-app1/-/npm-app1-1.0.0.tgz\",\n"
                        + "      \"id\": \"bnBtLXRlc3Q6OTEyZDBmZTdiODE5MjM5MjY2MTI1MTBiOGYyMTQ0NGI\",\n"
                        + "      \"repository\": \"npm-test\",\n"
                        + "      \"format\": \"npm\",\n"
                        + "      \"checksum\": {\n"
                        + "        \"sha1\": \"1a65e4a52b3e8387bfc5e47cf0544d4d7360df31\",\n"
                        + "        \"sha256\": \"392a08fb25d6d9ba75ef2ec104332dbc6fccee12ffa4d8da13b5070de85deea6\",\n"
                        + "        \"sha512\": \"cb8b62a94d5d3675ea1edd9398df8ee4278b6972fcde9f117fc033c493faeb4b8e26872f6345fab34ff20ad7e926234f171e4997f4df7bfce2245846c0fc348c\",\n"
                        + "        \"md5\": \"7fe206f8706959d8772c9c9c9cf23aba\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"continuationToken\": null\n"
                        + "}")
                    .withStatus(200)));
    wireMockRule3.stubFor(get(urlEqualTo("/nexus/repository/npm-test/npm-app1/-/npm-app1-1.0.0.tgz"))
                              .willReturn(aResponse().withBody(content.getBytes())));
    nexusService.downloadArtifacts(
        nexusThreeConfig, artifactStreamAttributes, artifactMetadata, null, null, null, listNotifyResponseData);
    assertThat(listNotifyResponseData.getData().size()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionForNugetNexus2x() {
    List<BuildDetails> buildDetails = nexusService.getVersion(
        RepositoryFormat.nuget.name(), nexusConfig, "MyNuGet2", "NuGet.Sample.Package", "1.0.0.8279");
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("1.0.0.8279");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("NuGet.Sample.Package-1.0.0.8279.nupkg");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(
            "http://localhost:8881/nexus/service/local/repositories/MyNuGet2/content/NuGet.Sample.Package/1.0.0.8279/NuGet.Sample.Package-1.0.0.8279.nupkg");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionForNPMNexus2x() {
    List<BuildDetails> buildDetails =
        nexusService.getVersion(RepositoryFormat.npm.name(), nexusConfig, "npm-internal", "npm-app1", "1.0.0");
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("1.0.0");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("npm-app1-1.0.0.tgz");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo("https://localhost:8881/nexus/content/repositories/npm-internal/npm-app1/-/npm-app1-1.0.0.tgz");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetVersionNexus2xMaven() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/mygroup/todolist/4.0/"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(XML_RESPONSE_INDEX_TREE_BROWSER_2)
                            .withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersion(nexusConfig, "releases", "mygroup", "todolist", null, null, "4.0");
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).containsExactly("4.0");

    assertThat(buildDetails)
        .hasSize(1)
        .extracting(BuildDetails::getBuildUrl)
        .containsExactly(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=war&e=war",
            wireMockRule.port()));
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("todolist-4.0.war");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=war&e=war",
            wireMockRule.port()));
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(1))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("todolist-4.0-sources.zip");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(1))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&e=zip&c=sources",
            wireMockRule.port()));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionWithExtensionAndClassifier() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/mygroup/todolist/4.0/"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(XML_RESPONSE_INDEX_TREE_BROWSER_2)
                            .withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersion(nexusConfig, "releases", "mygroup", "todolist", "zip", "sources", "4.0");
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).containsExactly("4.0");

    assertThat(buildDetails)
        .hasSize(1)
        .extracting(BuildDetails::getBuildUrl)
        .containsExactly(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&e=zip&c=sources",
            wireMockRule.port()));
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("todolist-4.0-sources.zip");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&e=zip&c=sources",
            wireMockRule.port()));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionWithExtensionNotProvidedAndClassifierProvided() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/mygroup/todolist/4.0/"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(XML_RESPONSE_INDEX_TREE_BROWSER_2)
                            .withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersion(nexusConfig, "releases", "mygroup", "todolist", "", "sources", "4.0");
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).containsExactly("4.0");

    assertThat(buildDetails)
        .hasSize(1)
        .extracting(BuildDetails::getBuildUrl)
        .contains(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&c=sources",
            wireMockRule.port()));
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("todolist-4.0-sources.zip");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&c=sources",
            wireMockRule.port()));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetVersionWithExtensionProvidedAndClassifierNotProvided() {
    wireMockRule.stubFor(
        get(urlEqualTo("/nexus/service/local/repositories/releases/index_content/mygroup/todolist/4.0/"))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(XML_RESPONSE_INDEX_TREE_BROWSER_2)
                            .withHeader("Content-Type", "application/xml")));

    List<BuildDetails> buildDetails =
        nexusService.getVersion(nexusConfig, "releases", "mygroup", "todolist", "zip", "", "4.0");
    assertThat(buildDetails).hasSize(1).extracting(BuildDetails::getNumber).contains("4.0");

    assertThat(buildDetails)
        .hasSize(1)
        .extracting(BuildDetails::getBuildUrl)
        .contains(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&e=zip",
            wireMockRule.port()));
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getFileName)
        .isEqualTo("todolist-4.0-sources.zip");
    assertThat(buildDetails.get(0).getArtifactFileMetadataList().get(0))
        .extracting(ArtifactFileMetadata::getUrl)
        .isEqualTo(String.format(
            "http://localhost:%d/nexus/service/local/artifact/maven/content?r=releases&g=mygroup&a=todolist&v=4.0&p=zip&e=zip",
            wireMockRule.port()));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldHandleExceptionsWhenGetGroupIdPaths() {
    wireMockRule.stubFor(
        post(urlPathMatching("/nexus/service/extdirect")).willReturn(new ResponseDefinitionBuilder().withStatus(401)));
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, null, null)).isInstanceOf(WingsException.class);

    wireMockRule.stubFor(
        post(urlPathMatching("/nexus/service/extdirect")).willReturn(new ResponseDefinitionBuilder().withStatus(405)));
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, null, null)).isInstanceOf(WingsException.class);

    wireMockRule.stubFor(
        post(urlPathMatching("/nexus/service/extdirect")).willReturn(new ResponseDefinitionBuilder().withStatus(502)));
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, null, null)).isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldReturnCorrectErrorMessagesForGetGroupIdPaths() throws Exception {
    NexusTwoServiceImpl nexusTwoService = Mockito.mock(NexusTwoServiceImpl.class);
    Reflect.on(nexusService).set("nexusTwoService", nexusTwoService);
    TimeLimiter timeLimiter = new FakeTimeLimiter();
    Reflect.on(nexusService).set("timeLimiter", timeLimiter);

    RuntimeException e = new RuntimeException(new TimeoutException());
    when(nexusTwoService.collectGroupIds(any(), eq("repo1"), any(), eq(null))).thenThrow(e);
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, "repo1", null))
        .isInstanceOf(ArtifactServerException.class);

    when(nexusTwoService.collectGroupIds(any(), eq("repo2"), any(), eq(null))).thenThrow(new RuntimeException());
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, "repo2", null))
        .isInstanceOf(ArtifactServerException.class);

    e = new RuntimeException(new XMLStreamException());
    when(nexusTwoService.collectGroupIds(any(), eq("repo3"), any(), eq(null))).thenThrow(e);
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, "repo3", null))
        .isInstanceOf(InvalidArtifactServerException.class);

    e = new RuntimeException(new SocketTimeoutException());
    when(nexusTwoService.collectGroupIds(any(), eq("repo4"), any(), eq(null))).thenThrow(e);
    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusConfig, "repo4", null))
        .isInstanceOf(ArtifactServerException.class);

    assertThatThrownBy(() -> nexusService.getGroupIdPaths(nexusThreeConfig, "repo4", null))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowUnsupportedExceptionOnGetVersionForNexus3() {
    assertThatThrownBy(()
                           -> nexusService.getVersion(
                               RepositoryFormat.npm.name(), nexusThreeConfig, "npm-internal", "npm-app1", "1.0.0"))
        .isInstanceOf(java.lang.UnsupportedOperationException.class)
        .hasMessage("Nexus 3.x does not support getVersion for parameterized artifact stream");

    assertThatThrownBy(
        () -> nexusService.getVersion(nexusThreeConfig, "npm-internal", "npm-app1", null, null, null, "1.0.0"))
        .isInstanceOf(java.lang.UnsupportedOperationException.class)
        .hasMessage("Nexus 3.x does not support getVersion for parameterized artifact stream");
  }
}
