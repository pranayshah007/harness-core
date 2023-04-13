// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package server

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"os/signal"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/secret"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/handler"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/server"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/store/bolt"
	"github.com/harness/harness-core/product/log-service/store/s3"
	"github.com/harness/harness-core/product/log-service/stream"
	"github.com/harness/harness-core/product/log-service/stream/memory"
	"github.com/harness/harness-core/product/log-service/stream/redis"
	"github.com/harness/harness-core/product/log-service/types"
	"github.com/harness/harness-core/product/platform/client"

	"github.com/joho/godotenv"
	"github.com/sirupsen/logrus"
	"gopkg.in/alecthomas/kingpin.v2"
)

type serverCommand struct {
	envfile string
}

func (c *serverCommand) run(*kingpin.ParseContext) error {
	godotenv.Load(c.envfile)

	ctx := context.Background()
	ctx, cancel := context.WithCancel(ctx)

	// load the system configuration from the environment.
	config, err := config.Load()
	if err != nil {
		logrus.WithError(err).
			Errorln("cannot load the service configuration")
		return err
	}

	// Parse the entire config to resolve any secrets (if required)
	err = secret.Resolve(ctx, config.SecretResolution.Enabled, config.SecretResolution.GcpProject,
		config.SecretResolution.GcpJsonPath, &config)
	if err != nil {
		logrus.WithError(err).
			Errorln("could not resolve secrets")
		return err
	}

	// init the system logging.
	initLogging(config)

	if config.Auth.DisableAuth {
		logrus.Warnln("log service is being started without auth, SHOULD NOT BE DONE FOR PROD ENVIRONMENTS")
	}

	var store store.Store
	if config.S3.Bucket != "" {
		// create the s3 store.
		logrus.Infof("configuring log store to use s3 compatible backend with endpoint: %s and bucket name: %s and ACL: %s",
			config.S3.Endpoint, config.S3.Bucket, config.S3.Acl)
		store = s3.NewEnv(
			config.S3.Bucket,
			config.S3.Prefix,
			config.S3.Endpoint,
			config.S3.PathStyle,
			config.S3.AccessKeyID,
			config.S3.AccessKeySecret,
			config.S3.Region,
			config.S3.Acl,
		)
	} else {
		// create the blob store.
		store, err = bolt.New(config.Bolt.Path)
		if err != nil {
			logrus.WithError(err).
				Fatalln("cannot initialize the bolt database")
			return err
		}

		logrus.Warnln("the bolt datastore is configured")
		logrus.Warnln("the bolt datastore is suitable for testing purposes only")
	}

	// create the stream server.
	var stream stream.Stream
	if config.Redis.Endpoint != "" {
		stream = redis.New(config.Redis.Endpoint, config.Redis.Password, config.Redis.SSLEnabled, config.Redis.DisableExpiryWatcher, config.Redis.CertPath)
		logrus.Infof("configuring log stream to use Redis: %s", config.Redis.Endpoint)
	} else {
		// create the in-memory stream
		stream = memory.New()
		logrus.Infoln("configuring log stream to use in-memory stream")
	}
	ngClient := client.NewHTTPClient(config.Platform.BaseURL, false, "")

	errorMsgChan := make(chan types.KeyErrorMsg, 1000)

	scheduleGPTRCAThread(ctx, store, errorMsgChan, config.Auth.OpenAPIToken)

	// create the http server.
	server := server.Server{
		Acme:    config.Server.Acme,
		Addr:    config.Server.Bind,
		Handler: handler.Handler(stream, store, config, ngClient, errorMsgChan),
	}

	// trap the os signal to gracefully shutdown the
	// http server.
	s := make(chan os.Signal, 1)
	signal.Notify(s, os.Interrupt)
	defer func() {
		signal.Stop(s)
		cancel()
	}()
	go func() {
		select {
		case val := <-s:
			logrus.Infof("received OS Signal to exit server: %s", val)
			cancel()
		case <-ctx.Done():
			logrus.Infoln("received a done signal to exit server")
		}
	}()

	logrus.Infof(fmt.Sprintf("server listening at port %s", config.Server.Bind))

	// starts the http server.
	err = server.ListenAndServe(ctx)
	if err == context.Canceled {
		logrus.Infoln("program gracefully terminated")
		return nil
	}

	if err != nil {
		logrus.Errorf("program terminated with error: %s", err)
	}

	return err
}

// Register the server commands.
func Register(app *kingpin.Application) {
	c := new(serverCommand)

	cmd := app.Command("server", "start the server").
		Action(c.run)

	cmd.Flag("env-file", "environment file").
		Default(".env").
		StringVar(&c.envfile)
}

// Get stackdriver to display logs correctly
// https://github.com/sirupsen/logrus/issues/403
// TODO: (Vistaar) Move to uber zap similar to other services
type OutputSplitter struct{}

func (splitter *OutputSplitter) Write(p []byte) (n int, err error) {
	if bytes.Contains(p, []byte("level=error")) {
		return os.Stderr.Write(p)
	}
	return os.Stdout.Write(p)
}

// helper function configures the global logger from
// the loaded configuration.
func initLogging(c config.Config) {
	logrus.SetOutput(&OutputSplitter{})
	l := logrus.StandardLogger()
	logger.L = logrus.NewEntry(l)
	if c.Debug {
		l.SetLevel(logrus.DebugLevel)
	}
	if c.Trace {
		l.SetLevel(logrus.TraceLevel)
	}
}

func scheduleGPTRCAThread(ctx context.Context, store store.Store, errorMsgChan <-chan types.KeyErrorMsg, OpenAPIToken string) {
	logrus.Info("Starting scheduleGPTRCAThread thread")
	go func() {
		for {
			select {
			case msg := <-errorMsgChan:
				go GPTRCAThread(ctx, store, msg, OpenAPIToken)
			case <-ctx.Done():
				return
			}
		}
	}()
}

func firstN(s string, n int) string {
	i := 0
	for j := range s {
		if i == n {
			return s[:j]
		}
		i++
	}
	return s
}

func GPTRCAThread(ctx context.Context, store store.Store, keyErrorMsg types.KeyErrorMsg, OpenAPIToken string) {
	fmt.Println(fmt.Sprintf("[RUTVIJ] IN GO THREAD: Received message %s", keyErrorMsg))
	//upload error message to new bucket
	if err := store.Upload(ctx, keyErrorMsg.Key+"/error-message", strings.NewReader(keyErrorMsg.ErrorMsg)); err != nil {
		logrus.Errorf("cannot upload error message object")
	}

	//process error message by chatgpt

	// Set up the HTTP request
	url := "https://api.openai.com/v1/chat/completions"
	reqContent := "These error messages are seen when running a Harness Continuous Integration step in a Cloud environment on an Ubuntu 22.04 Virtual Machine. Can you please tell me the root cause, possible solution and error category for each error as a Json list in the following format? Dont give me anything except the Json list. Please preserve the markdown.\n\n{\"Error\":, \"Cause\":, \"Solution\":, \"Category\": },\n]\n\nError Category must be classified from one of the following:\nCode error: These are the most obvious patterns to look for. They may include syntax errors, type errors, runtime errors, etc. Examples include \"SyntaxError\", \"TypeError\", \"NameError\", \"ValueError\", etc.\n\nAssertion failure: Assertion failures occur when a test fails. These failures often include a message that explains what went wrong. Examples include \"assertion failed\", \"assertion error\", \"test failed\", etc.\n\nFile not found error: These occur when a file that the build process depends on cannot be found. Examples include \"No such file or directory\", \"File not found\", \"Can't open file\", etc.\n\nNetwork error: These occur when the build process depends on external resources (e.g., APIs) and there are issues with connectivity or authentication. Examples include \"Connection refused\", \"Connection timed out\", \"401 Unauthorized\", etc.\n\nMemory error: These occur when the build process runs out of memory. Examples include \"Out of memory\", \"MemoryError\", \"malloc: out of memory\", etc.\n\nPermissions error: These occur when the build process does not have the required permissions to access a resource. Examples include \"Permission denied\", \"Access denied\", \"Operation not permitted\", etc.\n\nDependency issue: These occur when the build process depends on a library or package that is not installed or is incompatible with the current environment. Examples include \"ModuleNotFoundError\", \"ImportError\", \"Dependency not found\", etc.\n\nConfiguration error: These occur when the build process is not configured correctly. Examples include \"Invalid configuration\", \"Configuration error\", \"Missing configuration\", etc.\n\nHere is the error message:\n" + keyErrorMsg.ErrorMsg
	requestData := map[string]interface{}{
		"model": "gpt-3.5-turbo",
		"messages": []map[string]string{
			{"role": "user", "content": reqContent},
		},
		"temperature": 0.7,
	}
	requestDataBytes, err := json.Marshal(requestData)
	if err != nil {
		logrus.Errorf(err.Error())
		return
	}
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(requestDataBytes))
	if err != nil {
		logrus.Errorf(err.Error())
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+OpenAPIToken)

	// Send the HTTP request and parse the response
	client := http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		logrus.Errorf(err.Error())
		return
	}
	defer resp.Body.Close()

	responseBytes, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		logrus.Errorf(err.Error())
		return
	}

	// // Parse the response body into a Go data structure
	// var responseMap map[string]interface{}
	// err = json.Unmarshal(responseBytes, &responseMap)
	// if err != nil {
	//     panic(err)
	// }

	// // Access the content field of each choice
	// for _, choice := range responseMap["choices"].([]interface{}) {
	//     message := choice.(map[string]interface{})["message"].(map[string]interface{})
	//     content := message["content"].(string)
	// }

	respString := string(responseBytes)
	fmt.Println(fmt.Sprintf("[RUTVIJ] Printing ChatGPT response: %s", respString))
	//upload processed message by chatgpt
	if err := store.Upload(ctx, keyErrorMsg.Key+"/chatgpt-resp", strings.NewReader(respString)); err != nil {
		logrus.Errorf("cannot upload chatgpt response object")
	}
}
