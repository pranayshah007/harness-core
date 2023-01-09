package vo

//Response

import (
    "github.com/labstack/echo/v4"
) 


type AppConfigResponse struct {
    Status  int       `json:"status"`
    Message string    `json:"message"`
    Data    *echo.Map `json:"data"`
}