package router

import (
	"idp-service/configs"
	"idp-service/api"
	"idp-service/api/middlewares"

	"github.com/labstack/echo/v4"
)

func New() *echo.Echo {

	// create a new echo instance
	e := echo.New()

	//set all middlewares
	middlewares.SetMainMiddleWares(e)

	//configs
	configs.ConnectDB()

	//APIs
	api.AppConfigYaml(e)

	return e
}
