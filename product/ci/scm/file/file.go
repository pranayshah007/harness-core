// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package file

import (
	"context"
	"encoding/json"
	"io"
	"io/ioutil"

	"time"

	"github.com/drone/go-scm/scm"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/scm/git"
	"github.com/harness/harness-core/product/ci/scm/gitclient"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"go.uber.org/zap"
)

// FindFile returns the contents of a file based on a ref or branch.
func FindFile(ctx context.Context, fileRequest *pb.GetFileRequest, log *zap.SugaredLogger) (out *pb.FileContent, err error) {
	start := time.Now()
	log.Infow("FindFile starting", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("FindFile failure", "provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	ref, err := gitclient.GetValidRef(*fileRequest.GetProvider(), fileRequest.GetRef(), fileRequest.GetBranch())
	if err != nil {
		log.Errorw("Findfile failure, bad ref/branch", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	content, response, err := client.Contents.Find(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), ref)
	if err != nil {
		log.Errorw("Findfile failure", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. the file doesnt exist.
		out = &pb.FileContent{
			Status: int32(response.Status),
			Error:  err.Error(),
			Path:   fileRequest.GetPath(),
		}
		return out, nil
	}
	log.Infow("Findfile success", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "ref", ref, "commit id",
		content.Sha, "blob id", content.BlobID, "elapsed_time_ms", utils.TimeSince(start))

	commitID := string(content.Sha)
	if commitID == "" {
		// If the sha is not returned then we fetch the latest sha of the file
		request := &pb.GetLatestCommitOnFileRequest{
			Slug:     fileRequest.Slug,
			Branch:   fileRequest.GetBranch(),
			Provider: fileRequest.GetProvider(),
			FilePath: fileRequest.GetPath(),
		}
		response, err := git.GetLatestCommitOnFile(ctx, request, log)
		if err != nil {
			log.Errorw("GetLatest Commit Failed", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "ref", ref, "commit id",
				content.Sha, "blob id", content.BlobID, "elapsed_time_ms", utils.TimeSince(start))
			out = &pb.FileContent{
				Error:  "Could not fetch the file content",
				Status: 400,
				Path:   fileRequest.GetPath(),
			}
			return out, nil
		}
		commitID = response.GetCommitId()
	}

	out = &pb.FileContent{
		Content:  string(content.Data),
		CommitId: commitID,
		BlobId:   content.BlobID,
		Status:   int32(response.Status),
		Path:     fileRequest.Path,
	}
	return out, nil
}

// BatchFindFile returns the contents of a file based on a ref or branch.
func BatchFindFile(ctx context.Context, fileRequests *pb.GetBatchFileRequest, log *zap.SugaredLogger) (out *pb.FileBatchContentResponse, err error) {
	start := time.Now()
	log.Infow("BatchFindFile starting", "files", len(fileRequests.FindRequest))
	var store []*pb.FileContent
	for _, request := range fileRequests.FindRequest {
		file, err := FindFile(ctx, request, log)
		if err != nil {
			log.Errorw("BatchFindFile failure. Unable to get this file", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "path", request.GetPath(),
				"elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
			file = &pb.FileContent{
				Path:  request.GetPath(),
				Error: err.Error(),
			}
		}
		store = append(store, file)
	}
	out = &pb.FileBatchContentResponse{
		FileContents: store,
	}
	log.Infow("BatchFindFile complete", "number of files searched for ", len(fileRequests.FindRequest), "elapsed_time_ms", utils.TimeSince(start))
	return out, nil
}

// DeleteFile removes a file, based on a ref or branch. NB not many git vendors have this functionality.
func DeleteFile(ctx context.Context, fileRequest *pb.DeleteFileRequest, log *zap.SugaredLogger) (out *pb.DeleteFileResponse, err error) {
	start := time.Now()
	log.Infow("DeleteFile starting", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("DeleteFile failure", "bad provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	inputParams := new(scm.ContentParams)
	inputParams.Message = fileRequest.GetMessage()
	inputParams.Branch = fileRequest.GetBranch()
	// github uses blob id for update check, others use commit id
	switch fileRequest.GetProvider().Hook.(type) {
	case *pb.Provider_Github:
		inputParams.BlobID = fileRequest.GetBlobId()
	default:
		inputParams.Sha, err = getCommitIdIfEmptyInRequest(ctx, fileRequest.GetCommitId(), fileRequest.GetSlug(), fileRequest.GetBranch(),
			fileRequest.GetProvider(), log)
		if err != nil {
			return nil, err
		}
	}

	inputParams.Signature = scm.Signature{
		Name:  fileRequest.GetSignature().GetName(),
		Email: fileRequest.GetSignature().GetEmail(),
	}

	response, err := client.Contents.Delete(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), inputParams)
	if err != nil {
		log.Errorw("DeleteFile failure", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. the file doesnt exist.
		out = &pb.DeleteFileResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}
	// go-scm doesnt provide CRUD content parsing lets do it our self

	commitID, blobID := parseCrudResponse(ctx, client, response.Body, *fileRequest.GetProvider(), requestContext{Slug: fileRequest.Slug, Branch: fileRequest.Branch, FilePath: fileRequest.Path}, log)
	log.Infow("DeleteFile success", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start))
	out = &pb.DeleteFileResponse{
		Status:   int32(response.Status),
		CommitId: commitID,
		BlobId:   blobID,
	}
	return out, nil
}

// UpdateFile updates a file contents, A valid SHA is needed.
func UpdateFile(ctx context.Context, fileRequest *pb.FileModifyRequest, log *zap.SugaredLogger) (out *pb.UpdateFileResponse, err error) {
	start := time.Now()
	log.Infow("UpdateFile starting", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("UpdateFile failure", "bad provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	inputParams := new(scm.ContentParams)
	inputParams.Data = []byte(fileRequest.GetContent())
	inputParams.Message = fileRequest.GetMessage()
	inputParams.Branch = fileRequest.GetBranch()
	// github uses blob id for update check, others use commit id
	switch fileRequest.GetProvider().Hook.(type) {
	case *pb.Provider_Github:
		inputParams.BlobID = fileRequest.GetBlobId()
	default:
		inputParams.Sha, err = getCommitIdIfEmptyInRequest(ctx, fileRequest.GetCommitId(), fileRequest.GetSlug(), fileRequest.GetBranch(),
			fileRequest.GetProvider(), log)
		if err != nil {
			return nil, err
		}
	}

	inputParams.Signature = scm.Signature{
		Name:  fileRequest.GetSignature().GetName(),
		Email: fileRequest.GetSignature().GetEmail(),
	}

	response, err := client.Contents.Update(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), inputParams)

	if err != nil {
		log.Errorw("UpdateFile failure", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "branch", fileRequest.GetBranch(), "sha", inputParams.Sha, "branch", inputParams.Branch,
			"elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. the git tree has moved on.
		out = &pb.UpdateFileResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}
	// go-scm doesnt provide CRUD content parsing lets do it our self
	commitID, blobID := parseCrudResponse(ctx, client, response.Body, *fileRequest.GetProvider(), requestContext{Slug: fileRequest.Slug, Branch: fileRequest.Branch, FilePath: fileRequest.Path}, log)
	log.Infow("UpdateFile success", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "branch", fileRequest.GetBranch(), "sha", inputParams.Sha, "branch", inputParams.Branch,
		"elapsed_time_ms", utils.TimeSince(start))
	out = &pb.UpdateFileResponse{
		Status:   int32(response.Status),
		BlobId:   blobID,
		CommitId: commitID,
	}
	return out, nil
}

// PushFile creates a file if it does not exist, otherwise it updates it.
func PushFile(ctx context.Context, fileRequest *pb.FileModifyRequest, log *zap.SugaredLogger) (out *pb.FileContent, err error) {
	start := time.Now()
	out = &pb.FileContent{}
	log.Infow("PushFile starting", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("PushFile failure", "bad provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	ref, err := gitclient.GetValidRef(*fileRequest.GetProvider(), fileRequest.GetCommitId(), fileRequest.GetBranch())
	if err != nil {
		log.Errorw("PushFile failure, bad ref/branch", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	file, _, err := client.Contents.Find(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), ref)
	if err == nil {
		log.Infow("PushFile calling UpdateFile", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())
		// github uses blob id for update check, others use commit id
		switch fileRequest.GetProvider().Hook.(type) {
		case *pb.Provider_Github:
			fileRequest.BlobId = file.BlobID
		default:
			fileRequest.CommitId = file.Sha
		}
		updateResponse, updateErr := UpdateFile(ctx, fileRequest, log)
		if updateErr != nil {
			log.Errorw("PushFile failure, UpdateFile failed", "provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(),
				"elapsed_time_ms", utils.TimeSince(start), zap.Error(updateErr))
			return nil, updateErr
		}
		out.Status = updateResponse.Status
	} else {
		log.Infow("PushFile calling CreateFile", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())
		createResponse, createErr := CreateFile(ctx, fileRequest, log)
		if createErr != nil {
			log.Errorw("PushFile failure, CreateFile failed", "provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(),
				"elapsed_time_ms", utils.TimeSince(start), zap.Error(createErr))
			return nil, createErr
		}
		out.Status = createResponse.Status
	}
	file, _, err = client.Contents.Find(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), ref)
	if err != nil {
		log.Errorw("Findfile failure", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("UpsertFile success", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start))
	out.Path = fileRequest.GetPath()
	out.CommitId = file.Sha
	out.BlobId = file.BlobID
	out.Content = string(file.Data)

	return out, nil
}

// CreateFile creates a file with the passed through contents, it will fail if the file already exists.
func CreateFile(ctx context.Context, fileRequest *pb.FileModifyRequest, log *zap.SugaredLogger) (out *pb.CreateFileResponse, err error) {
	start := time.Now()
	log.Infow("CreateFile starting", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("CreateFile failure", "bad provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	inputParams := new(scm.ContentParams)
	inputParams.Data = []byte(fileRequest.GetContent())
	inputParams.Message = fileRequest.GetMessage()
	inputParams.Branch = fileRequest.GetBranch()
	inputParams.Signature = scm.Signature{
		Name:  fileRequest.GetSignature().GetName(),
		Email: fileRequest.GetSignature().GetEmail(),
	}
	// include the commitid if set, this is for azure
	inputParams.Ref, err = getCommitIdIfEmptyInRequest(ctx, fileRequest.GetCommitId(), fileRequest.GetSlug(), fileRequest.GetBranch(),
		fileRequest.GetProvider(), log)
	if err != nil {
		return nil, err
	}

	response, err := client.Contents.Create(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), inputParams)
	if err != nil {
		log.Errorw("CreateFile failure", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "branch", inputParams.Branch, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. the file exists.
		out = &pb.CreateFileResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}
	// go-scm doesnt provide CRUD content parsing lets do it our self
	commitID, blobID := parseCrudResponse(ctx, client, response.Body, *fileRequest.GetProvider(), requestContext{Slug: fileRequest.Slug, Branch: fileRequest.Branch, FilePath: fileRequest.Path}, log)
	log.Infow("CreateFile success", "slug", fileRequest.GetSlug(), "path", fileRequest.GetPath(), "branch", inputParams.Branch, "elapsed_time_ms", utils.TimeSince(start))
	out = &pb.CreateFileResponse{
		Status:   int32(response.Status),
		BlobId:   blobID,
		CommitId: commitID,
	}
	return out, nil
}

func FindFilesInBranch(ctx context.Context, fileRequest *pb.FindFilesInBranchRequest, log *zap.SugaredLogger) (out *pb.FindFilesInBranchResponse, err error) {
	start := time.Now()
	log.Infow("FindFilesInBranch starting", "slug", fileRequest.GetSlug())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("FindFilesInBranch failure", "bad provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	ref, err := gitclient.GetValidRef(*fileRequest.GetProvider(), fileRequest.GetRef(), fileRequest.GetBranch())
	if err != nil {
		log.Errorw("FindFilesInBranch failure, bad ref/branch", "provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "ref", ref, "filepath", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	files, response, err := client.Contents.List(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), ref, getCustomListOptsFindFilesInBranch(ctx, fileRequest))
	if err != nil {
		log.Errorw("FindFilesInBranch failure", "provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "ref", ref, "filepath", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	log.Infow("FindFilesInBranch success", "slug", fileRequest.GetSlug(), "ref", ref, "filepath", fileRequest.GetPath(), "elapsed_time_ms", utils.TimeSince(start))
	out = &pb.FindFilesInBranchResponse{
		File: convertContentList(files),
		Pagination: &pb.PageResponse{
			Next:    int32(response.Page.Next),
			NextUrl: response.Page.NextURL,
		},
	}
	return out, nil
}

func FindFilesInCommit(ctx context.Context, fileRequest *pb.FindFilesInCommitRequest, log *zap.SugaredLogger) (out *pb.FindFilesInCommitResponse, err error) {
	start := time.Now()
	log.Infow("FindFilesInCommit starting", "slug", fileRequest.GetSlug())

	client, err := gitclient.GetGitClient(*fileRequest.GetProvider(), log)
	if err != nil {
		log.Errorw("FindFilesInCommit failure", "bad provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	ref := fileRequest.GetRef()
	files, response, err := client.Contents.List(ctx, fileRequest.GetSlug(), fileRequest.GetPath(), ref, scm.ListOptions{Page: int(fileRequest.GetPagination().GetPage())})
	if err != nil {
		log.Errorw("FindFilesInCommit failure", "provider", gitclient.GetProvider(*fileRequest.GetProvider()), "slug", fileRequest.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("FindFilesInCommit success", "slug", fileRequest.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start))
	out = &pb.FindFilesInCommitResponse{
		File: convertContentList(files),
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func convertContentList(from []*scm.ContentInfo) (to []*pb.FileChange) {
	for _, v := range from {
		to = append(to, convertContent(v))
	}
	return to
}

func convertContent(from *scm.ContentInfo) *pb.FileChange {
	returnValue := &pb.FileChange{
		Path:     from.Path,
		CommitId: from.Sha,
		BlobId:   from.BlobID,
	}

	switch from.Kind.String() {
	case "file":
		returnValue.ContentType = pb.ContentType_FILE
	case "directory":
		returnValue.ContentType = pb.ContentType_DIRECTORY
	case "symlink":
		returnValue.ContentType = pb.ContentType_SYMLINK
	case "gitlink":
		returnValue.ContentType = pb.ContentType_GITLINK
	default:
		returnValue.ContentType = pb.ContentType_UNKNOWN_CONTENT
	}
	return returnValue
}

// this function is best effort ie if we cannot find the commit id or blob id do not error.
func parseCrudResponse(ctx context.Context, client *scm.Client, body io.Reader, p pb.Provider, request requestContext, log *zap.SugaredLogger) (commitID, blobID string) {
	bodyBytes, readErr := ioutil.ReadAll(body)
	if readErr != nil {
		log.Errorw("parseCrudResponse unable to read response from provider %p", gitclient.GetProvider(p), zap.Error(readErr))
		return "", ""
	}
	bodyStr := string(bodyBytes)
	switch p.GetHook().(type) {
	case *pb.Provider_Github:
		type githubResponse struct {
			Commit struct {
				Sha string `json:"sha"`
			} `json:"commit"`
			Content struct {
				Sha string `json:"sha"`
			} `json:"content"`
		}
		out := githubResponse{}
		err := json.Unmarshal([]byte(bodyStr), &out)
		// there is no commit id or sha, no need to error
		if err != nil {
			log.Errorw("parseCrudResponse unable to get commitid/blobid from Github CRUD operation", zap.Error(err))
			return "", ""
		}
		return out.Commit.Sha, out.Content.Sha
	case *pb.Provider_BitbucketCloud, *pb.Provider_BitbucketServer:
		// Bitbucket doesn't work on blobId concept for a file, thus it will  always be empty
		// We try to find out the latest commit on the file, which is most-likely the commit done by SCM itself
		// It works on best-effort basis
		request := &pb.GetLatestCommitOnFileRequest{
			Slug:     request.Slug,
			Branch:   request.Branch,
			Provider: &p,
			FilePath: request.FilePath,
		}
		response, err := git.GetLatestCommitOnFile(ctx, request, log)
		if err != nil {
			return "", ""
		}
		return response.CommitId, ""
	case *pb.Provider_Azure:
		// We try to find out the latest commit on the file, which is most-likely the commit done by SCM itself
		// It works on best-effort basis. The commit id is confusingly called the new object ID.
		type azureResponse struct {
			RefUpdates []struct {
				RepositoryID string `json:"repositoryId"`
				Name         string `json:"name"`
				OldObjectID  string `json:"oldObjectId"`
				NewObjectID  string `json:"newObjectId"`
			} `json:"refUpdates"`
		}
		out := azureResponse{}
		err := json.Unmarshal([]byte(bodyStr), &out)
		// there is no commit id or sha, no need to error
		if err != nil || len(out.RefUpdates) == 0 {
			log.Errorw("parseCrudResponse unable to get commitid/blobid from Azure CRUD operation", zap.Error(err))
			return "", ""
		}
		return out.RefUpdates[0].NewObjectID, ""
	default:
		return "", ""
	}
}

func getCustomListOptsFindFilesInBranch(ctx context.Context, fileRequest *pb.FindFilesInBranchRequest) scm.ListOptions {
	opts := &scm.ListOptions{Page: int(fileRequest.GetPagination().GetPage())}
	switch fileRequest.GetProvider().GetHook().(type) {
	case *pb.Provider_BitbucketCloud:
		if fileRequest.GetPagination().GetUrl() != "" {
			opts = &scm.ListOptions{URL: fileRequest.GetPagination().GetUrl()}
		}
	}

	return *opts
}

type requestContext struct {
	Slug     string
	Branch   string
	FilePath string
}

// getCommitIdIfEmptyInRequest returns the latest commit id of branch
// if commit id is already set in request then it will return the same commit-id
func getCommitIdIfEmptyInRequest(ctx context.Context, commitIdInRequest, slug, branch string, provider *pb.Provider, log *zap.SugaredLogger) (string, error) {
	if commitIdInRequest != "" {
		return commitIdInRequest, nil
	}
	// we only need to fetch the only commit-id for azure
	switch provider.GetHook().(type) {
	case *pb.Provider_Azure:
		resp, err := git.GetLatestCommit(ctx, &pb.GetLatestCommitRequest{
			Slug: slug,
			Type: &pb.GetLatestCommitRequest_Branch{
				Branch: branch,
			},
			Provider: provider,
		}, log)
		if err != nil {
			return "", err
		}
		return resp.GetCommitId(), nil
	default:
		return commitIdInRequest, nil
	}
}
