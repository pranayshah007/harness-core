// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"archive/zip"
	"context"
	"fmt"
	"github.com/harness/harness-core/product/log-service/stream"
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
func HandleListBlobWithPrefix(s store.Store, stream stream.Stream) http.HandlerFunc {
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

		executionID := ""
		if r.URL.Query().Get("executionID") != "" {
			executionID = r.URL.Query().Get("executionID")
		}

		out, _ := s.ListBlobPrefix(ctx, CreateAccountSeparatedKey(accountID, prefix))

		zipPrefix := prefix + "_" + executionID + "_logs.zip"

		go func(s store.Store, r http.Request) {
			fmt.Println("entrou na go routine")
			var wg sync.WaitGroup
			internal, cancel := context.WithCancel(context.Background())
			logger.WithContext(internal, logger.FromRequest(&r))

			pipeRead, pipeWrite := io.Pipe()

			zipWriter := zip.NewWriter(pipeWrite)

			fmt.Println("starting loop through keys")
			for batch, keys := range out {
				keys := keys
				batch := batch
				fmt.Println(batch)
				wg.Add(1)
				go func() {
					defer wg.Done()
					defer fmt.Println("Finish zip keys")
					fmt.Println("starting download keys", batch)
					for _, key := range keys {
						zipFile, err := zipWriter.Create(key)
						if err != nil {
							fmt.Println("erro ao criar arquivo no zip")
							cancel()
							return
						}
						fileDownloaded, err := s.Download(internal, key)
						if err != nil {
							fmt.Println("erro ao baixar arquivo")
							cancel()
							return
						}
						_, err = io.Copy(zipFile, fileDownloaded)
						if err != nil {
							fmt.Println("erro ao copiar conteudo do arquivo para o arquivo zip")
							cancel()
							return
						}
						err = fileDownloaded.Close()
						if err != nil {
							fmt.Println("erro ao fechar reader do arquivo")
							cancel()
							return
						}
					}
					fmt.Println("finalizou o zip")
				}()
			}

			go func() {
				fmt.Println("Start upload")
				err := s.Upload(internal, zipPrefix, pipeRead)
				if err != nil {
					fmt.Println("erro ao fazer o upload do arquivo zip")
					cancel()
				} else {
					fmt.Println("upload com sucesso")
				}
			}()

			go func() {
				wg.Wait()
				fmt.Println("fechando os pipewriter e zipwriter")
				zipWriter.Close()
				pipeWrite.Close()
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

func zipFile(ctx context.Context, keys []string, batch int, zipWriter *zip.Writer, s store.Store) error {
	for _, key := range keys {
		zipFile, err := zipWriter.Create(key)
		if err != nil {
			fmt.Println("erro ao criar arquivo no zip")
			return err
		}
		fileDownloaded, err := s.Download(ctx, key)
		if err != nil {
			fmt.Println("erro ao baixar arquivo")
			return err
		}
		_, err = io.Copy(zipFile, fileDownloaded)
		if err != nil {
			fmt.Println("erro ao copiar conteudo do arquivo para o arquivo zip")
			return err
		}
		err = fileDownloaded.Close()
		if err != nil {
			fmt.Println("erro ao fechar reader do arquivo")
			return err
		}
	}
	return nil
}
