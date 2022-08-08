/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.service;

import static io.harness.git.model.ChangeType.DELETE;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static software.wings.beans.yaml.Change.Builder.aFileChange;
import static software.wings.beans.yaml.GitFileChange.Builder.aGitFileChange;
import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.INDEX_YAML;
import static software.wings.beans.yaml.YamlConstants.MANIFEST_FILE_FOLDER;
import static software.wings.beans.yaml.YamlConstants.MANIFEST_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.SERVICES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.utils.Utils.generatePath;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.beans.Application;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.common.AuditHelper;
import software.wings.dl.WingsPersistence;
import software.wings.exception.YamlProcessingException;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.app.ApplicationYamlHandler;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.YamlOperationResponse;
import software.wings.yaml.YamlPayload;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.omg.CORBA.INVALID_ACTIVITY;

@Slf4j
public class YamlServiceImplTest extends WingsBaseTest {
  @Mock private YamlHandlerFactory yamlHandlerFactory;
  @Mock private YamlHelper yamlHelper;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private transient YamlGitService yamlGitService;
  @Mock private FeatureFlagService featureFlagService;
  @InjectMocks @Inject YamlServiceImpl yamlService = new YamlServiceImpl();
  @Spy @InjectMocks private AuditHelper auditHelper;
  @Mock private AuditService auditService;
  private static final String resourcePath = "400-rest/src/test/resources/yaml";
  @Mock ApplicationYamlHandler applicationYamlHandler;
  @Mock BaseYamlHandler baseYamlHandler;
  @Mock HarnessTagYamlHelper harnessTagYamlHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void processYamlFilesAsTar() throws IOException {
    InputStream zipfile = getClass().getResourceAsStream("yaml_zip_test.zip");

    List<GitFileChange> changeList = yamlService.getChangesForZipFile("TestAccountID", zipfile, null);
    assertThat(changeList.size()).isEqualTo(59);
    assertThat(changeList.stream()
                   .filter(change
                       -> change.getFilePath().equals(
                           "Setup_Master_Copy/Applications/Harness-on-prem/Services/MongoDB/Config Files/"))
                   .collect(toList()))
        .isEmpty();
  }

  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFilterInvalidFilePaths() throws Exception {
    List<GitFileChange> gitFileChange = new ArrayList<>();
    gitFileChange.add(aGitFileChange().withAccountId(ACCOUNT_ID).withFilePath("temp.yaml").build());

    List<GitFileChange> filteredGitFileChange = yamlService.filterInvalidFilePaths(gitFileChange);
    assertThat(filteredGitFileChange).isNotNull();
    assertThat(filteredGitFileChange).isEmpty();

    String validFilePath = "Setup/Applications/app1/Index.yaml";
    gitFileChange.add(aGitFileChange().withAccountId(ACCOUNT_ID).withFilePath(validFilePath).build());
    assertThat(gitFileChange).hasSize(2);

    filteredGitFileChange = yamlService.filterInvalidFilePaths(gitFileChange);
    assertThat(filteredGitFileChange).isNotNull();
    assertThat(filteredGitFileChange).hasSize(1);
    assertThat(filteredGitFileChange.get(0).getFilePath()).isEqualTo(validFilePath);

    gitFileChange.clear();
    gitFileChange.add(aGitFileChange().withAccountId(ACCOUNT_ID).withFilePath(validFilePath).build());
    filteredGitFileChange = yamlService.filterInvalidFilePaths(gitFileChange);
    assertThat(filteredGitFileChange).isNotNull();
    assertThat(filteredGitFileChange).hasSize(1);
    assertThat(filteredGitFileChange.get(0).getFilePath()).isEqualTo(validFilePath);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testIsProcessingAllowed() {
    YamlType[] types = YamlType.values();
    Change change = new Change();
    for (YamlType type : types) {
      assertThat(yamlService.isProcessingAllowed(change, type)).isEqualTo(true);
    }
  }
  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_computeProcessingOrder() {
    final Change change1 = aFileChange()
                               .withAccountId(ACCOUNT_ID)
                               .withFilePath(generatePath(PATH_DELIMITER, false, SETUP_FOLDER, APPLICATIONS_FOLDER,
                                   "app123", SERVICES_FOLDER, "service1", MANIFEST_FOLDER, INDEX_YAML))
                               .withChangeType(MODIFY)
                               .build();
    final Change change2 = aFileChange()
                               .withAccountId(ACCOUNT_ID)
                               .withFilePath(generatePath(PATH_DELIMITER, false, SETUP_FOLDER, APPLICATIONS_FOLDER,
                                   "app123", SERVICES_FOLDER, "service1", MANIFEST_FOLDER, INDEX_YAML))
                               .withChangeType(DELETE)
                               .build();
    final Change change3 = aFileChange()
                               .withAccountId(ACCOUNT_ID)
                               .withFilePath(generatePath(PATH_DELIMITER, false, SETUP_FOLDER, APPLICATIONS_FOLDER,
                                   "app123", SERVICES_FOLDER, "service1", INDEX_YAML))
                               .withChangeType(MODIFY)
                               .build();
    final Change change4 =
        aFileChange()
            .withAccountId(ACCOUNT_ID)
            .withFilePath(generatePath(PATH_DELIMITER, false, SETUP_FOLDER, APPLICATIONS_FOLDER, "app123",
                SERVICES_FOLDER, "service1", MANIFEST_FOLDER, MANIFEST_FILE_FOLDER, "vars.yaml"))
            .withChangeType(DELETE)
            .build();
    final ArrayList<Change> changeList = Lists.newArrayList(change1, change2, change3, change4);
    yamlService.sortByProcessingOrder(changeList);
    assertThat(changeList).isEqualTo(Arrays.asList(change4, change2, change3, change1));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidYamlNameExceptionWhenPhaseNameHasDots() {
    YamlPayload yamlPayload = new YamlPayload();
    yamlPayload.setYamlPayload("harnessApiVersion: '1.0'\n"
        + "type: ROLLING\n"
        + "concurrencyStrategy: INFRA\n"
        + "envName: qa\n"
        + "phases:\n"
        + "- type: KUBERNETES\n"
        + "  computeProviderName: Harness Sample K8s Cloud Provider\n"
        + "  daemonSet: false\n"
        + "  infraDefinitionName: K8s\n"
        + "  name: Rolling.\n");

    shouldThrowInvalidYamlNameException(yamlPayload);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidYamlNameExceptionWhenAmiTagNameIsEmpty() {
    YamlPayload yamlPayload = new YamlPayload();
    yamlPayload.setYamlPayload("harnessApiVersion: '1.0'\n"
        + "type: AMI\n"
        + "amiFilters:\n"
        + "- name: ami-image-id\n"
        + "  value: ss\n"
        + "amiTags:\n"
        + "- name: '         '\n"
        + "  value: '123'\n"
        + "region: us-east-1\n"
        + "serverName: aws-playground\n");

    shouldThrowInvalidYamlNameException(yamlPayload);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidYamlNameExceptionWhenAmiFilterNameIsEmpty() {
    YamlPayload yamlPayload = new YamlPayload();
    yamlPayload.setYamlPayload("harnessApiVersion: '1.0'\n"
        + "type: AMI\n"
        + "amiFilters:\n"
        + "- name: '   '\n"
        + "  value: ss\n"
        + "amiTags:\n"
        + "- name: 'tagName'\n"
        + "  value: '123'\n"
        + "region: us-east-1\n"
        + "serverName: aws-playground\n");

    shouldThrowInvalidYamlNameException(yamlPayload);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void shouldPerformYAMLOperationWithAuditsLogged() throws INVALID_ACTIVITY, FileNotFoundException {
    final AuditHeader auditHeader = AuditHeader.Builder.anAuditHeader().build();
    final String auditId = "AUDIT_ID";
    final String testAccountId = "TEST_ACCOUNT_ID";
    final Application application =
        Application.Builder.anApplication().name("Sample App").uuid("uuid").accountId(testAccountId).build();
    doReturn(auditId).when(auditService).getAuditHeaderIdFromGlobalContext();
    doReturn(auditHeader).when(wingsPersistence).get(AuditHeader.class, auditId);
    doReturn(applicationYamlHandler).when(yamlHandlerFactory).getYamlHandler(any(), any());
    doReturn(application).when(yamlHelper).getApp(anyString(), anyString());
    InputStream zipfile = setupInputStreamFromZipFile("Setup");
    final YamlOperationResponse yamlOperationResponse = yamlService.upsertYAMLFilesAsZip(testAccountId, zipfile);
    assertThat(yamlOperationResponse).isNotNull();
    verify(auditHelper, times(1)).setAuditContext(auditHeader);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testUpsertFromYamlWhenEntityIdInChangeDoesNotMatchTheYamlEntityId() throws Exception {
    GitFileChange change = GitFileChange.Builder.aGitFileChange()
                               .withChangeType(ChangeType.MODIFY)
                               .withFileContent("")
                               .withFilePath("Setup/Applications/app1/Index.yaml")
                               .withAccountId("TestAccountID")
                               .withEntityId("inputEntityId")
                               .build();
    ChangeContext changeContext =
        ChangeContext.Builder.aChangeContext().withChange(change).withYamlSyncHandler(applicationYamlHandler).build();
    when(applicationYamlHandler.get(any(), anyString(), any()))
        .thenReturn(Application.Builder.anApplication().uuid("newEntityId").build());
    yamlService.upsertFromYaml(changeContext, Collections.singletonList(changeContext));
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testUpsertFromYaml() throws Exception {
    GitFileChange change = GitFileChange.Builder.aGitFileChange()
                               .withChangeType(ChangeType.MODIFY)
                               .withFileContent("")
                               .withFilePath("Setup/Applications/app1/Index.yaml")
                               .withAccountId("TestAccountID")
                               .withEntityId("inputEntityId")
                               .build();
    ChangeContext changeContext =
        ChangeContext.Builder.aChangeContext().withChange(change).withYamlSyncHandler(applicationYamlHandler).build();
    when(applicationYamlHandler.get(any(), anyString(), any()))
        .thenReturn(Application.Builder.anApplication().uuid("inputEntityId").build());
    yamlService.upsertFromYaml(changeContext, Collections.singletonList(changeContext));
    verify(applicationYamlHandler, times(1)).upsertFromYaml(any(), anyList());
    verify(harnessTagYamlHelper, times(1)).upsertTagLinksIfRequired(any());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getFilesWhichAreSuccessfullyProcessedTest() throws Exception {
    GitFileChange change1 = getGitFileChange("TestAccountID", "Setup/Applications/app1/Index1.yaml", "inputEntityId1");
    GitFileChange change2 = getGitFileChange("TestAccountID", "Setup/Applications/app1/Index2.yaml", "inputEntityId1");
    GitFileChange change3 = getGitFileChange("TestAccountID", "Setup/Applications/app1/Index3.yaml", "inputEntityId1");
    GitFileChange change4 = getGitFileChange("TestAccountID", "Setup/Applications/app1/Index4.yaml", "inputEntityId1");
    GitFileChange change5 = getGitFileChange("TestAccountID", "Setup/Applications/app1/Index5.yaml", "inputEntityId1");

    ChangeContext changeContext1 = ChangeContext.Builder.aChangeContext().withChange(change1).build();
    ChangeContext changeContext2 = ChangeContext.Builder.aChangeContext().withChange(change2).build();
    ChangeContext changeContext3 = ChangeContext.Builder.aChangeContext().withChange(change3).build();
    ChangeContext changeContext4 = ChangeContext.Builder.aChangeContext().withChange(change4).build();
    ChangeContext changeContext5 = ChangeContext.Builder.aChangeContext().withChange(change5).build();

    List<ChangeContext> allChangeContexts =
        Arrays.asList(changeContext1, changeContext2, changeContext3, changeContext4, changeContext5);

    // Case where no files failed
    List<Change> filesWhichAreSuccessfullyProcessed =
        yamlService.getFilesWhichAreSuccessfullyProcessed(allChangeContexts, new ArrayList<>());
    assertThat(filesWhichAreSuccessfullyProcessed.size()).isEqualTo(5);

    List<Change> filesWhichAreSuccessfullyProcessed1 =
        yamlService.getFilesWhichAreSuccessfullyProcessed(allChangeContexts, Arrays.asList(change1, change2, change3));
    assertThat(filesWhichAreSuccessfullyProcessed1.size()).isEqualTo(2);

    List<Change> filesWhichAreSuccessfullyProcessed2 =
        yamlService.getFilesWhichAreSuccessfullyProcessed(allChangeContexts, Arrays.asList(change4, change5));
    assertThat(filesWhichAreSuccessfullyProcessed2.size()).isEqualTo(3);
  }

  private GitFileChange getGitFileChange(String accountId, String filePath, String entityId) {
    return aGitFileChange()
        .withChangeType(ChangeType.MODIFY)
        .withFileContent("")
        .withFilePath(filePath)
        .withAccountId(accountId)
        .withEntityId(entityId)
        .build();
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void testUpsertFromYamlForAccountDefaults() throws Exception {
    GitFileChange change =
        GitFileChange.Builder.aGitFileChange()
            .withChangeType(ChangeType.MODIFY)
            .withFileContent(
                "\"harnessApiVersion: '1.0'\\ntype: ACCOUNT_DEFAULTS\\ndefaults:\\n- name: SampleKey\\n  value: SampleValue3\\n\"")
            .withFilePath("Setup/Defaults.yaml")
            .withAccountId("TestAccountID")
            .withEntityId("inputEntityId")
            .build();
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withChange(change)
                                      .withYamlSyncHandler(baseYamlHandler)
                                      .withYamlType(YamlType.ACCOUNT_DEFAULTS)
                                      .build();
    verify(changeContext.getYamlSyncHandler(), times(0)).upsertFromYaml(any(), anyList());
    yamlService.upsertFromYaml(changeContext, Collections.singletonList(changeContext));
    verify(harnessTagYamlHelper, times(1)).upsertTagLinksIfRequired(any());
  }

  private void shouldThrowInvalidYamlNameException(YamlPayload yamlPayload) {
    GitFileChange change = GitFileChange.Builder.aGitFileChange()
                               .withChangeType(ChangeType.MODIFY)
                               .withFileContent(yamlPayload.getYaml())
                               .withFilePath(generatePath(PATH_DELIMITER, false, SETUP_FOLDER, APPLICATIONS_FOLDER,
                                   "app123", SERVICES_FOLDER, "service1", MANIFEST_FOLDER, INDEX_YAML))
                               .withAccountId("TestAccountID")
                               .build();

    assertThatThrownBy(() -> yamlService.processChangeSet(Collections.singletonList(change)))
        .isInstanceOf(YamlProcessingException.class)
        .hasMessageEndingWith("Error while processing some yaml files in the changeset.");
  }
  private InputStream setupInputStreamFromZipFile(final String fileName) throws FileNotFoundException {
    File file = null;
    file = new File(resourcePath + PATH_DELIMITER + fileName + ".zip");
    return new FileInputStream(file);
  }
}
