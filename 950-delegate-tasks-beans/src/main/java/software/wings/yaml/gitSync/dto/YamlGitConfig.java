package software.wings.yaml.gitSync.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import software.wings.jersey.JsonViews;
import software.wings.beans.dto.SettingAttribute;

public class YamlGitConfig {

    private String accountId;
    private String url;
    private String username;
    @JsonView(JsonViews.Internal.class) private char[] password;
    private software.wings.beans.dto.SettingAttribute sshSettingAttribute;
    private String sshSettingId;
    private boolean keyAuth;
    @JsonIgnore
    private String encryptedPassword;
    private String branchName;
}
