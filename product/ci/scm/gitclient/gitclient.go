// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package gitclient

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"fmt"
	"net/http"
	"net/http/httputil"
	"os"

	"github.com/drone/go-scm/scm"
	"github.com/drone/go-scm/scm/driver/azure"
	"github.com/drone/go-scm/scm/driver/bitbucket"
	"github.com/drone/go-scm/scm/driver/gitea"
	"github.com/drone/go-scm/scm/driver/github"
	"github.com/drone/go-scm/scm/driver/gitlab"
	"github.com/drone/go-scm/scm/driver/harness"
	"github.com/drone/go-scm/scm/driver/stash"
	"github.com/drone/go-scm/scm/transport"

	"github.com/drone/go-scm/scm/transport/oauth2"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func oauthTransport(token string, skip bool, additionalCertsPath string, log *zap.SugaredLogger) http.RoundTripper {
	return &oauth2.Transport{
		Base: defaultTransport(skip, additionalCertsPath, log),
		Source: oauth2.StaticTokenSource(
			&scm.Token{
				Token: token,
			},
		),
	}
}
func privateTokenTransport(token string, skip bool, additionalCertsPath string, log *zap.SugaredLogger) http.RoundTripper {
	return &transport.PrivateToken{
		Base:  defaultTransport(skip, additionalCertsPath, log),
		Token: token,
	}
}

func giteaTransport(token string, skip bool, additionalCertsPath string, log *zap.SugaredLogger) http.RoundTripper {
	return &oauth2.Transport{
		Base:   defaultTransport(skip, additionalCertsPath, log),
		Scheme: oauth2.SchemeBearer,
		Source: oauth2.StaticTokenSource(
			&scm.Token{
				Refresh: token,
				Token:   token,
			},
		),
	}
}

func tlsConfig(skip bool, additionalCertsPath string, log *zap.SugaredLogger) *tls.Config {
	config := tls.Config{
		InsecureSkipVerify: skip,
	}
	if skip || additionalCertsPath == "" {
		return &config
	}
	// Try to read 	additional certs and add them to the root CAs
	// Create TLS config using cert PEM
	rootPem, err := os.ReadFile(additionalCertsPath)
	if err != nil {
		log.Warnf("could not read certificate file (%s), error: %s", additionalCertsPath, err.Error())
		return &config
	}

	// Use the system certs if possible
	rootCAs, _ := x509.SystemCertPool()
	if rootCAs == nil {
		rootCAs = x509.NewCertPool()
	}

	ok := rootCAs.AppendCertsFromPEM(rootPem)
	if !ok {
		log.Errorf("error adding cert (%s) to pool, error: %s", additionalCertsPath, err.Error())
		return &config
	}
	config.RootCAs = rootCAs
	return &config
}

func bitbucketTransport(username, password string, skip bool, additionalCertsPath string, log *zap.SugaredLogger) http.RoundTripper {
	return &transport.BasicAuth{
		Base:     defaultTransport(skip, additionalCertsPath, log),
		Username: username,
		Password: password,
	}
}

// defaultTransport provides a default http.Transport.
// If skip verify is true, the transport will skip ssl verification.
// Otherwise, it will append all the certs from the provided path.
func defaultTransport(skip bool, additionalCertsPath string, log *zap.SugaredLogger) http.RoundTripper {
	return &http.Transport{
		Proxy:           http.ProxyFromEnvironment,
		TLSClientConfig: tlsConfig(skip, additionalCertsPath, log),
	}
}

func GetValidRef(p pb.Provider, inputRef, inputBranch string) (string, error) {
	if inputRef != "" {
		return inputRef, nil
	} else if inputBranch != "" {
		switch p.GetHook().(type) {
		case *pb.Provider_BitbucketCloud:
			return inputBranch, nil
		case *pb.Provider_BitbucketServer:
			return inputBranch, nil
		case *pb.Provider_Azure:
			return inputBranch, nil
		case *pb.Provider_Harness:
			return inputBranch, nil
		default:
			return scm.ExpandRef(inputBranch, "refs/heads"), nil
		}
	} else {
		return "", status.Error(codes.InvalidArgument, "Must provide a ref or a branch")
	}
}

func GetGitClient(p pb.Provider, log *zap.SugaredLogger) (client *scm.Client, err error) { //nolint:gocyclo,funlen
	switch p.GetHook().(type) {
	case *pb.Provider_Github:
		if p.GetEndpoint() == "" {
			client = github.NewDefault()
		} else {
			client, err = github.New(p.GetEndpoint())
			if err != nil {
				log.Errorw("GetGitClient failure Github", "endpoint", p.GetEndpoint(), zap.Error(err))
				return nil, err
			}
		}
		var token string
		switch p.GetGithub().GetProvider().(type) {
		case *pb.GithubProvider_AccessToken:
			token = p.GetGithub().GetAccessToken()
		default:
			return nil, status.Errorf(codes.Unimplemented, "Github Application not implemented yet")
		}
		client.Client = &http.Client{
			Transport: oauthTransport(token, p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
		}
	case *pb.Provider_Gitlab:
		if p.GetEndpoint() == "" {
			client = gitlab.NewDefault()
		} else {
			client, err = gitlab.New(p.GetEndpoint())
			if err != nil {
				log.Errorw("GetGitClient failure Gitlab", "endpoint", p.GetEndpoint(), zap.Error(err))
				return nil, err
			}
		}
		var token string
		switch p.GetGitlab().GetProvider().(type) {
		case *pb.GitlabProvider_AccessToken:
			token = p.GetGitlab().GetAccessToken()
			client.Client = &http.Client{
				Transport: oauthTransport(token, p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
			}
		case *pb.GitlabProvider_PersonalToken:
			token = p.GetGitlab().GetPersonalToken()
			client.Client = &http.Client{
				Transport: privateTokenTransport(token, p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
			}
		default:
			return nil, status.Errorf(codes.Unimplemented, "Gitlab provider not implemented yet")
		}

	case *pb.Provider_Gitea:
		if p.GetEndpoint() == "" {
			log.Error("getGitClient failure Gitea, endpoint is empty")
			return nil, status.Errorf(codes.InvalidArgument, fmt.Sprintf("Must provide an endpoint for %s", p.String()))
		}
		client, err = gitea.New(p.GetEndpoint())
		if err != nil {
			log.Errorw("GetGitClient failure Gitea", "endpoint", p.GetEndpoint(), zap.Error(err))
			return nil, err
		}
		client.Client = &http.Client{
			Transport: giteaTransport(p.GetGitea().GetAccessToken(), p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
		}
	case *pb.Provider_BitbucketCloud:
		client = bitbucket.NewDefault()
		if p.GetBitbucketCloud().GetAuthType() == pb.AuthType_OAUTH {
            client.Client = &http.Client{
        		Transport: oauthTransport(p.GetBitbucketCloud().GetOauthToken(), p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
        	}
		} else {
		    client.Client = &http.Client{
                Transport: bitbucketTransport(p.GetBitbucketCloud().GetUsername(), p.GetBitbucketCloud().GetAppPassword(), p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
            }
		}
	case *pb.Provider_BitbucketServer:
		if p.GetEndpoint() == "" {
			log.Error("getGitClient failure Bitbucket Server, endpoint is empty")
			return nil, status.Errorf(codes.InvalidArgument, fmt.Sprintf("Must provide an endpoint for %s", p.String()))
		}
		client, err = stash.New(p.GetEndpoint())
		if err != nil {
			log.Errorw("GetGitClient failure Bitbucket Server", "endpoint", p.GetEndpoint(), zap.Error(err))
			return nil, err
		}
		client.Client = &http.Client{
			Transport: bitbucketTransport(p.GetBitbucketServer().GetUsername(), p.GetBitbucketServer().GetPersonalAccessToken(), p.GetSkipVerify(), p.GetAdditionalCertsPath(), log),
		}
	case *pb.Provider_Azure:
		client = azure.NewDefault(p.GetAzure().GetOrganization(), p.GetAzure().GetProject())
		// prepend ':' and encode the access token as base64
		encodedToken := fmt.Sprintf(":%s", p.GetAzure().GetPersonalAccessToken())
		encodedToken = base64.StdEncoding.EncodeToString([]byte(encodedToken))
		client.Client = &http.Client{
			Transport: &transport.Custom{
				Before: func(r *http.Request) {
					r.Header.Set("Authorization", fmt.Sprintf("Basic %s", encodedToken))
				},
			},
		}
	case *pb.Provider_Harness:
		switch p.GetHarness().Provider.(type) {
		case *pb.HarnessProvider_HarnessJwt:
			// gitness
			client, err = harness.New(p.GetEndpoint(), p.GetHarness().GetHarnessJwt().GetAccount(), p.GetHarness().GetHarnessJwt().GetOrganization(), p.GetHarness().GetHarnessJwt().GetProject())
			if err != nil {
				log.Errorw("GetGitClient failure gitness", "endpoint", p.GetEndpoint(), zap.Error(err))
				return nil, err
			}
			client.Client = &http.Client{
				Transport: &transport.Custom{
					Before: func(r *http.Request) {
						r.Header.Set("Authorization", fmt.Sprintf("Basic %s", p.GetHarness().GetHarnessJwt().GetToken()))
					},
				},
			}
		case *pb.HarnessProvider_HarnessAccessToken:
			client, err = harness.New(p.GetEndpoint(),
				p.GetHarness().GetHarnessAccessToken().GetAccount(), p.GetHarness().GetHarnessAccessToken().GetOrganization(), p.GetHarness().GetHarnessAccessToken().GetProject())
			if err != nil {
				log.Errorw("GetGitClient failure Harness Platform", "endpoint", p.GetEndpoint(), zap.Error(err))
				return nil, err
			}
			client.Client = &http.Client{
				Transport: &transport.Custom{
					Before: func(r *http.Request) {
						r.Header.Set("x-api-key", p.GetHarness().GetHarnessAccessToken().GetAccessToken())
					},
				},
			}

		default:
			log.Errorw("GetGitClient unsupported Harness provider", "endpoint", p.GetEndpoint())
		}

	default:
		log.Errorw("GetGitClient unsupported git provider", "endpoint", p.GetEndpoint())
		return nil, status.Errorf(codes.InvalidArgument, "Unsupported git provider")
	}
	if p.Debug {
		client.DumpResponse = func(resp *http.Response, body bool) ([]byte, error) {
			out, err := httputil.DumpResponse(resp, body)
			if err != nil {
				log.Errorw("GetGitClient debug dump failed", "endpoint", p.GetEndpoint())
			}
			log.Infow("GetGitClient debug", "dump", string(out))
			return nil, nil
		}
	}
	return client, nil
}

// returns a string of the git provider being used. Currently only its name.
func GetProvider(p pb.Provider) string {
	switch p.GetHook().(type) {
	case *pb.Provider_Github:
		return "github"
	case *pb.Provider_Gitlab:
		return "gitlab"
	case *pb.Provider_Gitea:
		return "gitea"
	case *pb.Provider_BitbucketCloud:
		return "bitbucket cloud"
	case *pb.Provider_BitbucketServer:
		return "bitbucket server"
	case *pb.Provider_Azure:
		return "azure"
	case *pb.Provider_Harness:
		return "harness"
	default:
		return "unknown provider"
	}
}
