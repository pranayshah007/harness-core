// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"archive/zip"
	"context"
	"fmt"
	"github.com/google/uuid"
	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/queue"
	"io"
	"net/http"
	"sync"
	"time"

	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/store"
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

// HandleDownload returns an http.HandlerFunc that downloads
// a blob from the datastore and copies to the http.Response.
func HandleListBlobWithPrefix(q queue.Queue, s store.Store, cache cache.Cache) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		st := time.Now()
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		ctx := r.Context()

		accountID := r.FormValue(accountIDParam)
		prefix := ""
		if r.URL.Query().Get("prefix") != "" {
			prefix = r.URL.Query().Get("prefix")
		}

		out, _ := s.ListBlobPrefix(ctx, CreateAccountSeparatedKey(accountID, prefix))

		uid := uuid.NewString()
		zipPrefix := uid + "_logs.zip"

		q.Publish("")

		go func(s store.Store, r http.Request) {
			var wg sync.WaitGroup
			internal, cancel := context.WithCancel(context.Background())
			logger.WithContext(internal, logger.FromRequest(&r))

			logger.FromRequest(&r).
				WithField("prefix", prefix).
				WithField("time", time.Now().Format(time.RFC3339)).
				Debug("ziplog: starting zip and upload log files")

			pipeRead, pipeWrite := io.Pipe()

			zipWriter := zip.NewWriter(pipeWrite)

			// zip
			for _, keys := range out {
				keys := keys
				wg.Add(1)
				go func() {
					logger.FromRequest(&r).
						WithField("prefix", prefix).
						WithField("time", time.Now().Format(time.RFC3339)).
						Debug("ziplog: start zip files")

					err := zipFile(cancel, internal, keys, zipWriter, s, &wg)
					if err != nil {
						logger.FromRequest(&r).
							WithError(err).
							WithField("prefix", prefix).
							WithField("time", time.Now().Format(time.RFC3339)).
							Warnln("ziplog: cannot create file to zip")
					}

					logger.FromRequest(&r).
						WithField("prefix", prefix).
						WithField("time", time.Now().Format(time.RFC3339)).
						Debug("ziplog: successfully zip created")
				}()
			}

			// upload
			go func() {
				logger.FromRequest(&r).
					WithField("prefix", prefix).
					WithField("time", time.Now().Format(time.RFC3339)).
					Debug("ziplog: start upload to zip")

				err := s.Upload(internal, zipPrefix, pipeRead)
				if err != nil {
					fmt.Println("erro ao fazer o upload do arquivo zip")
					logger.FromRequest(&r).
						WithError(err).
						WithField("prefix", prefix).
						WithField("time", time.Now().Format(time.RFC3339)).
						Warnln("ziplog: cannot upload zip to s3")
					cancel()
					return
				}

				logger.FromRequest(&r).
					WithField("prefix", prefix).
					WithField("time", time.Now().Format(time.RFC3339)).
					Debug("ziplog: successfully zip file updated to s3")
			}()

			go func() {
				wg.Wait()
				zipWriter.Close()
				pipeWrite.Close()
				logger.FromRequest(&r).
					WithField("prefix", prefix).
					WithField("time", time.Now().Format(time.RFC3339)).
					Debug("ziplog: zip and pipe closed")
			}()

			return
		}(s, *r)

		link, err := s.DownloadLink(ctx, zipPrefix, time.Hour)
		if err != nil {
			WriteNotFound(w, err)
			logger.FromRequest(r).
				WithError(err).
				WithField("prefix", prefix).
				Errorln("api: cannot generate the download url")
		} else {
			req, _ := cache.Get(ctx, r.URL.String())
			if req == "" {
				fmt.Println("caching the req")
				err := cache.Create(ctx, r.URL.String(), link, "in progress")
				fmt.Println(err)
			}

			WriteJSON(w, struct {
				Link    string        `json:"link"`
				Expires time.Duration `json:"expires"`
			}{
				link, time.Hour,
			}, 200)

			logger.FromRequest(r).
				WithField("prefix", prefix).
				WithField("latency", time.Since(st)).
				WithField("time", time.Now().Format(time.RFC3339)).
				Infoln("api: successfully downloaded object")
		}
	}
}

func zipFile(cancel context.CancelFunc, internal context.Context, keys []string, zipWriter *zip.Writer, s store.Store, wg *sync.WaitGroup) error {
	defer wg.Done()
	for _, key := range keys {
		zipFile, err := zipWriter.Create(key)
		if err != nil {
			cancel()
			return err
		}
		fileDownloaded, err := s.Download(internal, key)
		if err != nil {
			cancel()
			return err
		}
		_, err = io.Copy(zipFile, fileDownloaded)
		if err != nil {
			cancel()
			return err
		}
		err = fileDownloaded.Close()
		if err != nil {
			cancel()
			return err
		}
	}
	return nil
}
