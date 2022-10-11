// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package cmd

import (
	"fmt"
	"net/http"

	"github.com/harness/harness-core/queue-service/hsqs/config"
	_ "github.com/harness/harness-core/queue-service/hsqs/docs"
	"github.com/harness/harness-core/queue-service/hsqs/handler"
	"github.com/harness/harness-core/queue-service/hsqs/router"
	"github.com/harness/harness-core/queue-service/hsqs/store/redis"
	"github.com/joho/godotenv"
	"github.com/labstack/echo/v4"

	"github.com/spf13/cobra"
	echoSwagger "github.com/swaggo/echo-swagger"
)

const envarg = "envfile"

// @title          Swagger Doc- hsqs
// @version        1.0
// @description    This is a queuing client.
// @termsOfService http://swagger.io/terms/

// @contact.name  API Support
// @contact.url   http://www.swagger.io/support
// @contact.email support@swagger.io

// @license.name Apache 2.0
// @license.url  http://www.apache.org/licenses/LICENSE-2.0.html

// @host     localhost:9091
// @BasePath /
// @schemes  http
var serverCmd = &cobra.Command{
	Use:   "server",
	Short: "Start hsqs server",
	Long:  `Start hsqs server`,
	Run: func(cmd *cobra.Command, args []string) {
		path, _ := cmd.Flags().GetString(envarg)
		err := godotenv.Load(path)
		if err != nil {
			panic("error parsing config file")
		}

		c, err := config.Load()
		if err != nil {
			panic("error loading config")
		}
		startServer(c)
	},
}

func startServer(c *config.Config) {

	r := router.New(c.Debug)
	r.GET("/swagger/*", echoSwagger.WrapHandler)
	r.GET("/", func(c echo.Context) error {
		return c.String(http.StatusOK, "Hello, World!")
	})

	g := r.Group("v1")

	store := redis.NewRedisStore(c.Redis.Endpoint)
	h := handler.NewHandler(store)
	h.Register(g)

	err := r.Start(fmt.Sprintf("%s:%s", c.Server.Host, c.Server.PORT))
	if err != nil {
		r.Logger.Fatalf(err.Error())
	}
}

func init() {
	rootCmd.AddCommand(serverCmd)
	serverCmd.Flags().StringP(envarg, "e", "local.env", "Config file for the server")
}
