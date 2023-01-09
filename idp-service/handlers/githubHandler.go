package handlers

import (
	"idp-service/vo"
	"net/http"
	"strings"
	"encoding/base64"
	// "bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	// "log"
	// "net/http"
)


func GetSHAOfFile(repoOwnerName string, repoName string, filePath string, branchName string, token string ) string {
    fmt.Println("Extracting SHA of file to be updated")
	client := &http.Client{}
	req, err := http.NewRequest("GET", "https://api.github.com/repos/"+repoOwnerName+"/"+repoName+"/contents/"+filePath+"?ref="+branchName, nil)
	if err != nil {
	fmt.Print(err.Error())
	}
	req.Header.Add("Accept", "application/vnd.github+json")
	req.Header.Add("X-GitHub-Api-Version", "2022-11-28")
	req.Header.Add("Authorization", "Bearer "+token)
	resp, err := client.Do(req)
	if err != nil {
	fmt.Print(err.Error())
	}

    defer resp.Body.Close()
    bodyBytes, _ := ioutil.ReadAll(resp.Body)

    // Convert response body to string
    bodyString := string(bodyBytes)

	//convertong string to jsonmap
	var jsonMap map[string]interface{}
    json.Unmarshal([]byte(bodyString), &jsonMap)
    fmt.Print("API Response as struct %+v\n", jsonMap,"\n\n\n")
	return jsonMap["sha"].(string)
}


func UpdateFileInGithubRepo(repoOwnerName string, repoName string, filePath string, branchName string, token string, commitMessage string, sha string, commiterName string, email string, content string) {
    fmt.Println("Updating the file in github repo")
	client := &http.Client{}
	body := vo.Body
	encodedText := base64.StdEncoding.EncodeToString([]byte(content))

	// req, err := http.NewRequest("PUT", "https://api.github.com/repos/"+repoOwnerName+"/"+repoName+"/contents/"+filePath+"?ref="+branchName,strings.NewReader(fmt.Sprintf(body, commitMessage, sha, commiterName, email, content, branchName)))
	req, err := http.NewRequest("PUT", fmt.Sprintf("https://api.github.com/repos/%s/%s/contents/%s?ref=%s", repoOwnerName, repoName, filePath, branchName),strings.NewReader(fmt.Sprintf(body, commitMessage, sha, commiterName, email, encodedText, branchName)))

	if err != nil {
	fmt.Print(err.Error())
	}
	req.Header.Add("Accept", "application/vnd.github+json")
	req.Header.Add("X-GitHub-Api-Version", "2022-11-28")
	req.Header.Add("Authorization", "Bearer "+token)

	resp, err := client.Do(req)
	if err != nil {
	fmt.Print(err.Error())
	}

    defer resp.Body.Close()
    bodyBytes, _ := ioutil.ReadAll(resp.Body)

    // Convert response body to string
    bodyString := string(bodyBytes)

	//convertong string to jsonmap
	var jsonMap map[string]interface{}
    json.Unmarshal([]byte(bodyString), &jsonMap)
    fmt.Print("API Response as struct %+v\n", jsonMap,"\n\n\n")

}
