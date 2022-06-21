// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package repo

import (
	"context"
	"fmt"
	"time"

	"github.com/drone/go-scm/scm"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/scm/gitclient"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"go.uber.org/zap"
)

var githubWebhookMap map[string]pb.GithubWebhookEvent = map[string]pb.GithubWebhookEvent{
	"create":              pb.GithubWebhookEvent_GITHUB_CREATE,
	"delete":              pb.GithubWebhookEvent_GITHUB_DELETE,
	"deployment":          pb.GithubWebhookEvent_GITHUB_DEPLOYMENT,
	"issue":               pb.GithubWebhookEvent_GITHUB_ISSUE,
	"issue_comment":       pb.GithubWebhookEvent_GITHUB_ISSUE_COMMENT,
	"pull_request":        pb.GithubWebhookEvent_GITHUB_PULL_REQUEST,
	"pull_request_review": pb.GithubWebhookEvent_GITHUB_PULL_REQUEST_REVIEW,
	"push":                pb.GithubWebhookEvent_GITHUB_PUSH,
}

func CreateWebhook(ctx context.Context, request *pb.CreateWebhookRequest, log *zap.SugaredLogger) (out *pb.CreateWebhookResponse, err error) {
	start := time.Now()
	log.Infow("CreateWebhook starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("CreateWebhook failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	inputParams := scm.HookInput{
		Name:       request.GetName(),
		Target:     request.GetTarget(),
		Secret:     request.GetSecret(),
		SkipVerify: request.GetSkipVerify(),
	}
	// for native events we convert enums to strings, which the gitprovider expects
	switch request.GetProvider().GetHook().(type) {
	case *pb.Provider_Azure:
		// special handling for azure
		return createWebhookForAzure(ctx, log, request, client, inputParams, start)
	case *pb.Provider_BitbucketCloud:
		eventStrings := convertBitbucketCloudEnumToStrings(request.GetNativeEvents().GetBitbucketCloud())
		inputParams.NativeEvents = eventStrings
	case *pb.Provider_BitbucketServer:
		eventStrings := convertBitbucketServerEnumToStrings(request.GetNativeEvents().GetBitbucketServer())
		inputParams.NativeEvents = eventStrings
	case *pb.Provider_Github:
		eventStrings := convertGithubEnumToStrings(request.GetNativeEvents().GetGithub())
		inputParams.NativeEvents = eventStrings
	case *pb.Provider_Gitlab:
		// NB we use scm events not native events like the others.
		events := convertGitlabEnumToHookEvents(request.GetNativeEvents().GetGitlab())
		inputParams.Events = events
	default:
		return nil, fmt.Errorf("there is no logic to convertEnumsToStrings, for this provider %s", gitclient.GetProvider(*request.GetProvider()))
	}

	hook, response, err := client.Repositories.CreateHook(ctx, request.GetSlug(), &inputParams)
	if err != nil {
		return handleCreateWebhookErrorResponse(ctx, err, log, request, response, start)
	}

	return handleCreateWebhookSuccessResponse(ctx, log, request, hook, response, start)
}

func createWebhookForAzure(ctx context.Context, log *zap.SugaredLogger, request *pb.CreateWebhookRequest, client *scm.Client, inputParams scm.HookInput,
	startTime time.Time) (out *pb.CreateWebhookResponse, err error) {
	// We need repoID for azure create webhook api (Find API supports both repoName and repoID)
	slug := getSlugIDForAzureRepo(ctx, request.GetSlug(), client)

	var (
		hook     *scm.Hook
		response *scm.Response
		events   []string
	)

	eventStrings := convertAzureEnumToStrings(request.GetNativeEvents().GetAzure())
	for _, es := range eventStrings {
		inputParamsCopy := inputParams
		inputParamsCopy.NativeEvents = []string{es}
		hook, response, err = client.Repositories.CreateHook(ctx, slug, &inputParamsCopy)

		if err != nil {
			return handleCreateWebhookErrorResponse(ctx, err, log, request, response, startTime)
		}
		// azure returns only one event
		events = append(events, hook.Events...)
	}

	hook.Events = events
	return handleCreateWebhookSuccessResponse(ctx, log, request, hook, response, startTime)
}

func handleCreateWebhookErrorResponse(_ context.Context, err error, log *zap.SugaredLogger, request *pb.CreateWebhookRequest, response *scm.Response,
	startTime time.Time) (*pb.CreateWebhookResponse, error) {
	log.Errorw("CreateWebhook failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "name", request.GetName(), "target", request.GetTarget(),
		"elapsed_time_ms", utils.TimeSince(startTime), zap.Error(err))
	// this is a hard error with no response
	if response == nil {
		return nil, err
	}
	// this is an error from the git provider, e.g. the hook exists.
	out := &pb.CreateWebhookResponse{
		Status: int32(response.Status),
		Error:  err.Error(),
	}
	return out, nil
}

func handleCreateWebhookSuccessResponse(_ context.Context, log *zap.SugaredLogger, request *pb.CreateWebhookRequest, hook *scm.Hook, response *scm.Response,
	startTime time.Time) (out *pb.CreateWebhookResponse, err error) {
	log.Infow("CreateWebhook success", "slug", request.GetSlug(), "name", request.GetName(), "target", request.GetTarget(), "elapsed_time_ms", utils.TimeSince(startTime))

	out = &pb.CreateWebhookResponse{
		Webhook: &pb.WebhookResponse{
			Id:         hook.ID,
			Name:       hook.Name,
			Target:     hook.Target,
			Active:     hook.Active,
			SkipVerify: hook.SkipVerify,
		},
		Status: int32(response.Status),
	}
	// convert event strings to enums
	nativeEvents, mappingErr := nativeEventsFromStrings(hook.Events, *request.GetProvider())
	if mappingErr != nil {
		log.Errorw("CreateWebhook mapping failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "name", request.GetName(), "target", request.GetTarget(),
			"elapsed_time_ms", utils.TimeSince(startTime), zap.Error(err))
		return nil, mappingErr
	}
	out.Webhook.NativeEvents = nativeEvents
	return out, nil
}

func DeleteWebhook(ctx context.Context, request *pb.DeleteWebhookRequest, log *zap.SugaredLogger) (out *pb.DeleteWebhookResponse, err error) {
	start := time.Now()
	log.Infow("DeleteWebhook starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("DeleteWebhook failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	response, err := client.Repositories.DeleteHook(ctx, request.GetSlug(), request.GetId())
	if err != nil {
		log.Errorw("DeleteWebhook failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "id", request.GetId(),
			"elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. authentication.
		out = &pb.DeleteWebhookResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
	}
	log.Infow("DeleteWebhook success", "slug", request.GetSlug(), "id", request.GetId(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.DeleteWebhookResponse{
		Status: int32(response.Status),
	}
	return out, nil
}

func ListWebhooks(ctx context.Context, request *pb.ListWebhooksRequest, log *zap.SugaredLogger) (out *pb.ListWebhooksResponse, err error) {
	start := time.Now()
	log.Infow("ListWebhooks starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("ListWebhooks failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	slug := request.GetSlug()
	switch request.GetProvider().GetHook().(type) {
	// need repoID for azure
	case *pb.Provider_Azure:
		slug = getSlugIDForAzureRepo(ctx, slug, client)
	}

	scmHooks, response, err := client.Repositories.ListHooks(ctx, slug, scm.ListOptions{Page: int(request.GetPagination().GetPage())})
	if err != nil {
		log.Errorw("ListWebhooks failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. authentication.
		out = &pb.ListWebhooksResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
	}
	log.Infow("ListWebhooks success", "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start))
	var hooks []*pb.WebhookResponse
	for _, h := range scmHooks {
		webhookResponse := pb.WebhookResponse{
			Id:         h.ID,
			Name:       h.Name,
			Target:     h.Target,
			Active:     h.Active,
			SkipVerify: h.SkipVerify,
		}
		// convert event strings to enums
		nativeEvents, mappingErr := nativeEventsFromStrings(h.Events, *request.GetProvider())
		if mappingErr != nil {
			log.Errorw("ListWebhooks mapping failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
			return nil, mappingErr
		}
		webhookResponse.NativeEvents = nativeEvents
		hooks = append(hooks, &webhookResponse)
	}

	out = &pb.ListWebhooksResponse{
		Webhooks: hooks,
		Status:   int32(response.Status),
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func nativeEventsFromStrings(sliceOfStrings []string, p pb.Provider) (nativeEvents *pb.NativeEvents, err error) {
	switch p.GetHook().(type) {
	case *pb.Provider_BitbucketCloud:
		bitbucketCloudEvents := convertStringsToBitbucketCloudEnum(sliceOfStrings)
		nativeEvents = &pb.NativeEvents{NativeEvents: &bitbucketCloudEvents}
	case *pb.Provider_BitbucketServer:
		bitbucketServerEvents := convertStringsToBitbucketServerEnum(sliceOfStrings)
		nativeEvents = &pb.NativeEvents{NativeEvents: &bitbucketServerEvents}
	case *pb.Provider_Github:
		githubEvents := convertStringsToGithubEnum(sliceOfStrings)
		nativeEvents = &pb.NativeEvents{NativeEvents: &githubEvents}
	case *pb.Provider_Gitlab:
		gitlabEvents := convertStringsToGitlabEnum(sliceOfStrings)
		nativeEvents = &pb.NativeEvents{NativeEvents: &gitlabEvents}
	case *pb.Provider_Azure:
		azureEvents := convertStringsToAzureEnum(sliceOfStrings)
		nativeEvents = &pb.NativeEvents{NativeEvents: &azureEvents}
	default:
		return nil, fmt.Errorf("there is no logic to convertStringsToEnums, for this provider %s", gitclient.GetProvider(p))
	}
	return nativeEvents, nil
}

func convertStringsToGitlabEnum(strings []string) (enums pb.NativeEvents_Gitlab) {
	// We make the slice of strings into a map, This makes looking up the enum fast and simple
	var gitlabWebhookMap = map[string]pb.GitlabWebhookEvent{
		"comment": pb.GitlabWebhookEvent_GITLAB_COMMENT,
		"issues":  pb.GitlabWebhookEvent_GITLAB_ISSUES,
		"merge":   pb.GitlabWebhookEvent_GITLAB_MERGE,
		"push":    pb.GitlabWebhookEvent_GITLAB_PUSH,
		"tag":     pb.GitlabWebhookEvent_GITLAB_TAG,
	}
	var array []pb.GitlabWebhookEvent
	for _, s := range strings {
		value, exists := gitlabWebhookMap[s]
		// ignore events we don't know about.
		if exists {
			array = append(array, value)
		}
	}
	enums.Gitlab = &pb.GitlabWebhookEvents{Events: array}
	return enums
}

func convertGitlabEnumToHookEvents(enums *pb.GitlabWebhookEvents) (events scm.HookEvents) {
	for _, e := range enums.GetEvents() {
		// ignore events we don't know about.
		switch e {
		case pb.GitlabWebhookEvent_GITLAB_COMMENT:
			events.IssueComment = true
		case pb.GitlabWebhookEvent_GITLAB_ISSUES:
			events.Issue = true
		case pb.GitlabWebhookEvent_GITLAB_MERGE:
			events.PullRequest = true
		case pb.GitlabWebhookEvent_GITLAB_PUSH:
			events.Push = true
		case pb.GitlabWebhookEvent_GITLAB_TAG:
			events.Tag = true
		}
	}
	return events
}

func convertStringsToGithubEnum(strings []string) (enums pb.NativeEvents_Github) {
	var array []pb.GithubWebhookEvent
	for _, s := range strings {
		value, exists := githubWebhookMap[s]
		// ignore events we don't know about.
		if exists {
			array = append(array, value)
		}
	}
	enums.Github = &pb.GithubWebhookEvents{Events: array}
	return enums
}

func convertGithubEnumToStrings(enums *pb.GithubWebhookEvents) (strings []string) {
	for _, e := range enums.GetEvents() {
		for key, value := range githubWebhookMap {
			// ignore events we don't know about.
			if e == value {
				strings = append(strings, key)
			}
		}
	}
	return strings
}

func convertStringsToBitbucketCloudEnum(strings []string) (enums pb.NativeEvents_BitbucketCloud) {
	m := make(map[string]bool)
	for i := 0; i < len(strings); i++ {
		m[strings[i]] = true
	}
	var array []pb.BitbucketCloudWebhookEvent
	// To ensure webhook integrity we make sure that every event string is present before setting the enum.
	// IE if a setting a single event is removed from the group then the enum is not set.
	if m["issue:created"] && m["issue:updated"] {
		array = append(array, pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_ISSUE)
	}
	if m["issue:comment_created"] {
		array = append(array, pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_ISSUE_COMMENT)
	}
	if m["pullrequest:updated"] && m["pullrequest:unapproved"] && m["pullrequest:approved"] && m["pullrequest:rejected"] && m["pullrequest:fulfilled"] && m["pullrequest:created"] {
		array = append(array, pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_PULL_REQUEST)
	}
	if m["pullrequest:comment_created"] && m["pullrequest:comment_updated"] && m["pullrequest:comment_deleted"] {
		array = append(array, pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_PULL_REQUEST_COMMENT)
	}
	if m["repo:push"] {
		array = append(array, pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_PUSH)
	}
	enums.BitbucketCloud = &pb.BitbucketCloudWebhookEvents{Events: array}
	return enums
}

func convertBitbucketCloudEnumToStrings(enums *pb.BitbucketCloudWebhookEvents) (strings []string) {
	for _, e := range enums.GetEvents() {
		// To make bitbucket cloud easier to use, some events are grouped together.
		// They have to be set together, and on the read side all events must be present for the enum to be set. This ensures integrity.
		switch e {
		case pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_ISSUE:
			strings = append(strings, "issue:created", "issue:updated")
		case pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_ISSUE_COMMENT:
			strings = append(strings, "issue:comment_created")
		case pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_PULL_REQUEST:
			strings = append(strings, "pullrequest:updated", "pullrequest:unapproved", "pullrequest:approved", "pullrequest:rejected", "pullrequest:fulfilled", "pullrequest:created")
		case pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_PULL_REQUEST_COMMENT:
			strings = append(strings, "pullrequest:comment_created", "pullrequest:comment_updated", "pullrequest:comment_deleted")
		case pb.BitbucketCloudWebhookEvent_BITBUCKET_CLOUD_PUSH:
			strings = append(strings, "repo:push")
		}
	}
	return strings
}

func convertAzureEnumToStrings(enums *pb.AzureWebhookEvents) (strings []string) {
	for _, e := range enums.GetEvents() {
		switch e {
		case pb.AzureWebhookEvent_AZURE_PUSH:
			strings = append(strings, "git.push")
		case pb.AzureWebhookEvent_AZURE_PULLREQUEST_CREATED:
			strings = append(strings, "git.pullrequest.created")
		case pb.AzureWebhookEvent_AZURE_PULLREQUEST_UPDATED:
			strings = append(strings, "git.pullrequest.updated")
		case pb.AzureWebhookEvent_AZURE_PULLREQUEST_MERGED:
			strings = append(strings, "git.pullrequest.merged")
		}
	}
	return strings
}

func convertStringsToAzureEnum(strings []string) (enums pb.NativeEvents_Azure) {
	var array []pb.AzureWebhookEvent
	for _, e := range strings {
		switch e {
		case "git.push":
			array = append(array, pb.AzureWebhookEvent_AZURE_PUSH)
		case "git.pullrequest.created":
			array = append(array, pb.AzureWebhookEvent_AZURE_PULLREQUEST_CREATED)
		case "git.pullrequest.updated":
			array = append(array, pb.AzureWebhookEvent_AZURE_PULLREQUEST_UPDATED)
		case "git.pullrequest.merged":
			array = append(array, pb.AzureWebhookEvent_AZURE_PULLREQUEST_MERGED)
		}
	}

	enums.Azure = &pb.AzureWebhookEvents{Events: array}
	return enums
}

func convertStringsToBitbucketServerEnum(strings []string) (enums pb.NativeEvents_BitbucketServer) {
	m := make(map[string]bool)
	for i := 0; i < len(strings); i++ {
		m[strings[i]] = true
	}
	var array []pb.BitbucketServerWebhookEvent
	// To ensure webhook integrity we make sure that every event string is present before setting the enum.
	// IE if a setting a single event is removed from the group then the enum is not set.
	if m["repo:refs_changed"] {
		array = append(array, pb.BitbucketServerWebhookEvent_BITBUCKET_SERVER_BRANCH_PUSH_TAG)
	}
	if m["pr:declined"] && m["pr:modified"] && m["pr:deleted"] && m["pr:opened"] && m["pr:merged"] {
		array = append(array, pb.BitbucketServerWebhookEvent_BITBUCKET_SERVER_PR)
	}
	if m["pr:comment:added"] && m["pr:comment:deleted"] && m["pr:comment:edited"] {
		array = append(array, pb.BitbucketServerWebhookEvent_BITBUCKET_SERVER_PR_COMMENT)
	}
	enums.BitbucketServer = &pb.BitbucketServerWebhookEvents{Events: array}
	return enums
}

func convertBitbucketServerEnumToStrings(enums *pb.BitbucketServerWebhookEvents) (strings []string) {
	for _, e := range enums.GetEvents() {
		// To make bitbucket server easier to use, some events are grouped together.
		// They have to be set together, and on the read side all events must be present for the enum to be set. This ensures integrity.
		switch e {
		case pb.BitbucketServerWebhookEvent_BITBUCKET_SERVER_BRANCH_PUSH_TAG:
			strings = append(strings, "repo:refs_changed")
		case pb.BitbucketServerWebhookEvent_BITBUCKET_SERVER_PR:
			strings = append(strings, "pr:declined", "pr:modified", "pr:deleted", "pr:opened", "pr:merged")
		case pb.BitbucketServerWebhookEvent_BITBUCKET_SERVER_PR_COMMENT:
			strings = append(strings, "pr:comment:added", "pr:comment:deleted", "pr:comment:edited")
		}
	}
	return strings
}

func getSlugIDForAzureRepo(ctx context.Context, slug string, client *scm.Client) string {
	slugValue := slug
	repo, resp, err := client.Repositories.Find(ctx, slug)
	if err == nil && resp != nil {
		slugValue = repo.ID
	}
	return slugValue
}
