from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from app.domain.errors import InvalidPlanCriteriaError, ProviderUnavailableError


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(InvalidPlanCriteriaError)
    async def handle_invalid_criteria(_: Request, exc: InvalidPlanCriteriaError) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={"detail": str(exc)},
        )

    @app.exception_handler(ProviderUnavailableError)
    async def handle_provider_unavailable(_: Request, exc: ProviderUnavailableError) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content={"detail": str(exc)},
        )