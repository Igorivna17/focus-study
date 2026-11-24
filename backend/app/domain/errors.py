class DomainError(Exception):
    """Базовий виняток бізнес-логіки FocusStudy."""

class InvalidPlanCriteriaError(DomainError):
    """Некоректні вхідні параметри для побудови плану."""

class ProviderUnavailableError(DomainError):
    """Зовнішній AI-провайдер тимчасово недоступний або повернув некоректну відповідь."""