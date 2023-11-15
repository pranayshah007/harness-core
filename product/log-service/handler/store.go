// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	gcputils "github.com/harness/harness-core/commons/go/lib/gcputils"

	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/entity"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/queue"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/platform/client"
)

const (
	filePathSuffix     = "logs.zip"
	maxItemsToDownload = 1500
	harnessDownload    = "harness-download"
	storage            = "/storage/"
	authTokenheader    = "Authorization"
	vanity             = "-vanity"
	NA                 = "NA" //Refers to cache key for accounts that dont have any vanity url
)

// HandleUpload returns an http.HandlerFunc that uploads
// a blob to the datastore.
func HandleUpload(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		if err := store.Upload(ctx, key, r.Body); err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot upload object")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully uploaded object")
		w.WriteHeader(http.StatusNoContent)
	}
}

// HandleUploadLink returns an http.HandlerFunc that generates
// a signed link to upload a blob to the datastore.
func HandleUploadLink(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		st := time.Now()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))
		expires := time.Hour

		link, err := store.UploadLink(ctx, key, expires)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot generate upload url")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully created upload link")
		WriteJSON(w, struct {
			Link    string        `json:"link"`
			Expires time.Duration `json:"expires"`
		}{link, expires}, 200)
	}
}

// HandleDownload returns an http.HandlerFunc that downloads
// a blob from the datastore and copies to the http.Response.
func HandleDownload(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		cookieToken, err := r.Cookie("token")
		if err != nil {
			fmt.Println("Could not fetch token from Cookie blob: %v", err)
		}
		fmt.Println("Cookkie token in  blob===", cookieToken)

		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		out, err := store.Download(ctx, key)
		if out != nil {
			defer out.Close()
		}
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot download the object")
		} else {
			io.Copy(w, out)
			logger.FromRequest(r).
				WithField("key", key).
				WithField("latency", time.Since(st)).
				WithField("time", time.Now().Format(time.RFC3339)).
				Infoln("api: successfully downloaded object")
		}
	}
}

// HandleDownloadLink returns an http.HandlerFunc that generates
// a signed link to download a blob to the datastore.
func HandleDownloadLink(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		st := time.Now()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))
		expires := time.Hour

		link, err := store.DownloadLink(ctx, key, expires)
		if err != nil {
			WriteInternalError(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot generate download url")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully created download url")
		WriteJSON(w, struct {
			Link    string        `json:"link"`
			Expires time.Duration `json:"expires"`
		}{link, expires}, 200)
	}
}

// HandleDelete returns an http.HandlerFunc that deletes
// a blob from the datastore.
func HandleDelete(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		st := time.Now()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		if err := store.Delete(ctx, key); err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: cannot delete object")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully deleted object")
		w.WriteHeader(http.StatusNoContent)
	}
}

func HandleInternalDelete(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		go store.DeleteWithPrefix(ctx, key)
		w.WriteHeader(http.StatusNoContent)
	}
}

func HandleZipLinkPrefix(q queue.Queue, s store.Store, c cache.Cache, cfg config.Config, gcsClient gcputils.GCS, ngClient *client.HTTPClient) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.URL.Query().Get(accountIDParam)
		prefix := r.URL.Query().Get(usePrefixParam)

		zipPrefix := CreateAccountSeparatedKey(accountID, prefix) + "/" + filePathSuffix

		link, err := s.DownloadLink(ctx, zipPrefix, cfg.CacheTTL)
		if err != nil {
			logger.FromRequest(r).
				WithError(err).
				WithField(usePrefixParam, prefix).
				Errorln("api: cannot generate the download url")
			WriteNotFound(w, err)
			return
		}
		if cfg.S3.ReverseProxyEnabled {
			link, err = GetSignedURL(link, zipPrefix, cfg, gcsClient)
			if err != nil {
				logger.FromRequest(r).
					WithError(err).
					WithField(usePrefixParam, prefix).
					Errorln("api: cannot Sign the download url")
				WriteNotFound(w, err)
				return
			}
			if cfg.Platform.VanityURLEnabled {
				//Get vanity URL from cache if it exists else calculate
				vanityURL, err := getVanityURLFromCache(ctx, accountID, prefix, c, cfg, r)
				if err != nil {
					logger.FromRequest(r).
						WithError(err).
						WithField("prefix", prefix).
						Errorln("api: cannot fetch vanityURL from cache")
				}
				if vanityURL == "" {
					logger.FromRequest(r).WithField("prefix", prefix).Infoln("vanity URL does not exists in cache fetching from platform")
					vanityURL, err = getVanityURLFromPlatform(ctx, accountID, c, cfg, r, ngClient)
					if err != nil {
						logger.FromRequest(r).
							WithError(err).
							WithField("prefix", prefix).
							Errorln("api: cannot fetch vanityURL from platform")
					}
				}
				if vanityURL != "" && vanityURL != NA {
					link, err = replaceVanityURL(vanityURL, link, prefix)
					if err != nil {
						logger.FromRequest(r).
							WithError(err).
							WithField("vanity_url", vanityURL).
							WithField("prefix", prefix).
							Warnln("api: cannot replace with vanity URL")
					} else {
						logger.FromRequest(r).WithField("prefix", prefix).Infoln("successfully replaced with vanity url")
					}
				} else {
					logger.FromRequest(r).WithField("prefix", prefix).Infoln("vanity url does not exist for account")
				}
			}
		}

		out, err := s.ListBlobPrefix(ctx, CreateAccountSeparatedKey(accountID, prefix), cfg.Zip.LIMIT_FILES)

		if err != nil || len(out) == 0 {
			logger.FromRequest(r).
				WithError(err).
				WithField(usePrefixParam, prefix).
				Errorln("api: cannot list files blob to download")
			WriteNotFound(w, fmt.Errorf("cannot list files for prefix"))
			return
		}

		if len(out) > maxItemsToDownload {
			err := errors.New("Amount of data is too large to download")
			logger.FromRequest(r).
				WithError(err).
				WithField(usePrefixParam, prefix).
				Errorln("api: Download failed! Amount of data is too large")
			WriteInternalError(w, fmt.Errorf("Prefix Key Exceeds Maximum Download Limit"))
			return
		}

		// creates a cache in status queued
		logger.FromRequest(r).WithField("Prefix", prefix).Infoln("Adding request to queued state for further processing")
		info := entity.ResponsePrefixDownload{}
		info.Status = entity.QUEUED
		info.Value = link
		info.Expire = time.Now().Add(cfg.CacheTTL)

		err = c.Create(ctx, prefix, info, cfg.CacheTTL)
		if err != nil {
			logger.FromRequest(r).
				WithError(err).
				WithField("url", r.URL.String()).
				WithField("Prefix", prefix).
				WithField("time", time.Now().Format(time.RFC3339)).
				WithField("info", info).
				Errorln("api: cannot create cache info")
			WriteInternalError(w, err)
			return
		}

		// produce event to queue
		err = q.Produce(ctx, cfg.ConsumerWorker.StreamName, prefix, zipPrefix, out)
		if err != nil {
			logger.FromRequest(r).
				WithError(err).
				WithField(usePrefixParam, prefix).
				Errorln("api: cannot produce message to queue")
			WriteInternalError(w, err)
			return
		}

		WriteUnescapeJSON(w, info, 200)
		logger.FromRequest(r).
			WithField(usePrefixParam, prefix).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully list prefix finished and produced to queue")
	}
}

func HandleExists(store store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		st := time.Now()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		key := CreateAccountSeparatedKey(accountID, r.FormValue(keyParam))

		exists, err := store.Exists(ctx, key)
		if err != nil {
			logger.FromRequest(r).
				WithError(err).
				WithField("key", key).
				Errorln("api: couldn't check existence of objects")
			return
		}

		logger.FromRequest(r).
			WithField("key", key).
			WithField("latency", time.Since(st)).
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("api: successfully checked existence of objects")

		io.WriteString(w, fmt.Sprintf("%t", exists))
	}
}

// GetSignedURL return a signed gcs object url
func GetSignedURL(link, zipPrefix string, cfg config.Config, gcsClient gcputils.GCS) (string, error) {
	link, err := gcsClient.SignURL(cfg.S3.Bucket, zipPrefix, cfg.S3.CustomHost, cfg.CacheTTL)
	if err != nil {
		return "", err
	}
	//parse and unescape only the link before harness-download otherwise it will corrupt the signed link
	lstring := strings.Split(link, harnessDownload)

	if len(lstring) < 2 {
		return "", fmt.Errorf("cannot parse Unescaped Signed url for url %s", link)
	}
	link, err = url.PathUnescape(lstring[0])
	if err != nil {
		return "", err
	}
	link = link + harnessDownload + lstring[1]
	return link, nil
}

// replaceVanityURL replaces the link with vanityURL of that account
func replaceVanityURL(vanityURL, link, prefix string) (string, error) {
	lstring := strings.SplitN(link, storage, 2)

	if len(lstring) >= 1 {
		link = vanityURL + storage + lstring[1]
		return link, nil
	}
	return link, fmt.Errorf("Error Splitting Vanity URL %s", link)
}

// getVanityURLFromCache gets the vanity URL from cache for the account if it exists
func getVanityURLFromCache(ctx context.Context, accountID, prefix string, c cache.Cache, cfg config.Config, r *http.Request) (string, error) {
	exists := c.Exists(ctx, accountID+vanity)
	if exists {
		vanityURLBytes, err := c.Get(ctx, accountID+vanity)
		if err != nil {
			return string(vanityURLBytes), err
		} else {
			return string(vanityURLBytes), nil
		}
	}
	return "", fmt.Errorf("Vanity URL does not exist in cache")
}

// getVanityURLFromPlatform gets the vanity URL for the account
func getVanityURLFromPlatform(ctx context.Context, accountID string, c cache.Cache, cfg config.Config, r *http.Request, ngClient *client.HTTPClient) (string, error) {
	cookieToken, err := r.Cookie("token")
	if err != nil {
		return "", fmt.Errorf("Could not fetch token from Cookie: %v", err)
	}

	//Check null token
	vanityURL, err := ngClient.GetVanityURL(ctx, accountID, "Bearer "+cookieToken.Value)
	if err != nil {
		return vanityURL, fmt.Errorf("api: cannot fetch the vanity url: %v", err)
	}
	if vanityURL == "" {
		//Add NA to cache if vanity URL does not exist for account
		err = c.Create(ctx, accountID+vanity, NA, cfg.Platform.VanityURLTTL)
		if err != nil {
			return vanityURL, fmt.Errorf("api: cannot create cache for vanity URL: %v", err)
		}
		return NA, fmt.Errorf("api: vanity_url does not exist for account setting dummy")
	}
	err = c.Create(ctx, accountID+vanity, vanityURL, cfg.Platform.VanityURLTTL)
	if err != nil {
		return vanityURL, fmt.Errorf("api: cannot create cache for vanity URL: %v", err)
	}
	return vanityURL, nil
}
