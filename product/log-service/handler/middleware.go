// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/dchest/authcookie"
	"github.com/harness/harness-core/product/platform/client"

	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/entity"
	"github.com/harness/harness-core/product/log-service/logger"
)

const authHeader = "X-Harness-Token"
const authAPIKeyHeader = "x-api-key"
const routingIDparam = "routingId"

// TokenGenerationMiddleware is middleware to ensure that the incoming request is allowed to
// invoke token-generation endpoints.
func TokenGenerationMiddleware(config config.Config, validateAccount bool, ngClient *client.HTTPClient) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if validateAccount {
				accountID := r.FormValue(accountIDParam)
				if accountID == "" {
					WriteBadRequest(w, errors.New("no account ID in query params"))
					return
				}
			}

			//Get X-api-key from header, if not then check for token
			inputApiKey := r.Header.Get(authAPIKeyHeader)
			if inputApiKey != "" {
				err := doApiKeyAuthentication(inputApiKey, r.FormValue(accountIDParam), r.FormValue(routingIDparam), ngClient)
				if err != nil {
					WriteBadRequest(w, errors.New("apikey in request not authorized for receiving tokens"))
					return
				}
			} else {
				// Try to get token from the header or the URL param
				inputToken := r.Header.Get(authHeader)
				if inputToken == "" {
					inputToken = r.FormValue(authHeader)
				}

				if inputToken == "" {
					WriteBadRequest(w, errors.New("no token or x-api-key in header"))
					return
				}

				if inputToken != config.Auth.GlobalToken {
					// Error: invalid token
					WriteBadRequest(w, errors.New("token in request not authorized for receiving tokens"))
					return
				}
			}

			next.ServeHTTP(w, r)
		})
	}
}

// GetAuthFunc is middleware to ensure that the incoming request is allowed to access resources
// at the specific accountID
func AuthMiddleware(config config.Config, ngClient *client.HTTPClient, skipKeyCheck bool) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {

			accountID := r.FormValue(accountIDParam)
			if accountID == "" {
				WriteBadRequest(w, errors.New("no account ID in query params"))
				return
			}

			// Try to get token from the header or the URL param
			inputApiKey := r.Header.Get(authAPIKeyHeader)
			if inputApiKey != "" {
				err := doApiKeyAuthentication(inputApiKey, r.FormValue(accountIDParam), r.FormValue(routingIDparam), ngClient)
				if err != nil {
					WriteBadRequest(w, errors.New("apikey in request not authorized for receiving tokens"))
					return
				}
			} else {
				inputToken := r.Header.Get(authHeader)
				if inputToken == "" {
					inputToken = r.FormValue(authHeader)
				}

				if inputToken == "" {
					WriteBadRequest(w, errors.New("no token in header"))
					return
				}
				// accountID in token should be same as accountID in URL
				secret := []byte(config.Auth.LogSecret)
				login := authcookie.Login(inputToken, secret)
				if login == "" || login != accountID {
					WriteBadRequest(w, errors.New(fmt.Sprintf("operation not permitted for accountID: %s", accountID)))
					return
				}
			}

			// Validate that a key field is present in the request
			if !skipKeyCheck && r.FormValue(keyParam) == "" {
				WriteBadRequest(w, errors.New("no key exists in the URL"))
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func doApiKeyAuthentication(inputApiKey, accountID, routingId string, ngClient *client.HTTPClient) error {
	err := ngClient.ValidateApiKey(context.Background(), accountID, routingId, inputApiKey)
	return err
}

func CacheRequest(c cache.Cache) func(handler http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx := r.Context()
			logger.WithContext(ctx, logger.FromRequest(r))

			var info entity.ResponsePrefixDownload
			prefix := r.URL.Query().Get(usePrefixParam)
			exists := c.Exists(ctx, prefix)

			if exists {
				inf, err := c.Get(ctx, prefix)
				if err != nil {
					logger.FromRequest(r).
						WithError(err).
						WithField("url", r.URL.String()).
						WithField("time", time.Now().Format(time.RFC3339)).
						Errorln("middleware cache: cannot get prefix")
					WriteInternalError(w, err)
					return
				}

				err = json.Unmarshal(inf, &info)
				if err != nil {
					logger.FromRequest(r).
						WithError(err).
						WithField("url", r.URL.String()).
						WithField("time", time.Now().Format(time.RFC3339)).
						WithField("info", inf).
						Errorln("middleware cache: failed to unmarshal info")
					WriteInternalError(w, err)
					return
				}

				switch info.Status {
				case entity.QUEUED:
				case entity.IN_PROGRESS:
					WriteJSON(w, info, 200)
					return
				case entity.ERROR:
					err := c.Delete(ctx, prefix)
					if err != nil {
						logger.FromRequest(r).
							WithError(err).
							WithField("url", r.URL.String()).
							WithField("time", time.Now().Format(time.RFC3339)).
							WithField("info", inf).
							Errorln("middleware cache: failed to delete error in c")
						WriteInternalError(w, err)
						return
					}
					WriteJSON(w, info, 200)
					return
				case entity.SUCCESS:
					WriteJSON(w, info, 200)
					return
				default:
					next.ServeHTTP(w, r)
					return
				}
			}
			next.ServeHTTP(w, r)
			return
		})
	}
}

func RequiredQueryParams(params ...string) func(handler http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			for _, param := range params {
				value := r.FormValue(param)
				if len(value) == 0 || value == "" {
					err := errors.New(fmt.Sprintf("parameter %s is required.", param))
					WriteNotFound(w, err)
					logger.FromRequest(r).
						WithField("url", r.URL.String()).
						WithField("time", time.Now().Format(time.RFC3339)).
						WithError(err).
						Errorln("middleware validate query params: doesnt contain query param", param)
					return
				}
			}
			next.ServeHTTP(w, r)
		})
	}
}

func ValidatePrefixRequest() func(handler http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			containRunSequence := strings.Contains(r.URL.String(), "runSequence")
			if containRunSequence {
				logger.WithContext(context.Background(), logger.FromRequest(r))
				logger.FromRequest(r).
					WithField("url", r.URL.String()).
					WithField("time", time.Now().Format(time.RFC3339)).
					Debug("middleware validate: contain execution in prefix")
				next.ServeHTTP(w, r)
				return
			}

			err := errors.New(fmt.Sprintf("operation not permitted for prefix: %s", r.URL.String()))
			WriteBadRequest(w, err)
			logger.FromRequest(r).
				WithField("url", r.URL.String()).
				WithField("time", time.Now().Format(time.RFC3339)).
				WithError(err).
				Errorln("middleware validate: doesnt contain execution in prefix")
			return
		})
	}
}
