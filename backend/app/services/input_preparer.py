import re
from dataclasses import dataclass
from app.domain.errors import InvalidPlanCriteriaError
from app.domain.models import PlanCriteria

NON_STUDY_PATTERNS = (
    r"^купити\s+",
    r"^сходити\s+в\s+",
    r"^прибрати\s+",
    r"^замовити\s+їжу",
    r"^подивитись\s+серіал",
)

KEYBOARD_MASH_SUBSTRINGS = (
    "qwerty",
    "asdf",
    "zxcv",
    "йцукен",
    "фіва",
    "ячсм",
)


@dataclass(frozen=True)
class PreparedInput:
    criteria: PlanCriteria
    key_focus_terms: list[str]
    is_suitable_for_study: bool
    unsuitable_reason: str | None = None


def prepare_plan_input(raw: PlanCriteria) -> PreparedInput:
    """Етап підготовки даних: очищення, виділення ключових акцентів з контексту та перевірка придатності."""
    cleaned_topic = " ".join(raw.topic.strip().split())
    if len(cleaned_topic) < 3:
        raise InvalidPlanCriteriaError("Тема навчання повинна містити щонайменше 3 непробільні символи.")

    cleaned_context: str | None = None
    if raw.study_context:
        normalized_ctx = " ".join(raw.study_context.strip().split())
        if normalized_ctx:
            cleaned_context = normalized_ctx[:500]

    # Виділяємо ключові слова з контексту користувача (довші за 3 символи)
    focus_terms: list[str] = []
    if cleaned_context:
        raw_words = re.findall(r"[А-Яа-яІіЇїЄєҐґA-Za-z0-9\-]{4,}", cleaned_context)
        seen: set[str] = set()
        for word in raw_words:
            lowered = word.lower()
            if lowered not in seen:
                seen.add(lowered)
                focus_terms.append(word)
            if len(focus_terms) >= 5:
                break

    # 1. Перевірка на наявність літер
    letters_only = re.sub(r"[^А-Яа-яІіЇїЄєҐґA-Za-z]", "", cleaned_topic)
    if len(letters_only) < 3:
        return PreparedInput(
            criteria=PlanCriteria(cleaned_topic, raw.available_minutes, raw.difficulty, cleaned_context),
            key_focus_terms=focus_terms,
            is_suitable_for_study=False,
            unsuitable_reason="Тема не містить змістовних слів (лише цифри або спецсимволи). Вкажіть конкретну навчальну тему.",
        )

    # 2. Перевірка на клавіатурний шум (рядок клавіш або 5+ приголосних поспіль без голосних)
    lowered_topic = cleaned_topic.lower()
    consonants_streak = re.search(
        r"[бвгґджзйклмнпрстфхцчшщbcdfghjklmnpqrstvwxz]{5,}",
        lowered_topic,
    )
    has_keyboard_mash = any(seq in lowered_topic for seq in KEYBOARD_MASH_SUBSTRINGS)
    if consonants_streak or has_keyboard_mash:
        return PreparedInput(
            criteria=PlanCriteria(cleaned_topic, raw.available_minutes, raw.difficulty, cleaned_context),
            key_focus_terms=focus_terms,
            is_suitable_for_study=False,
            unsuitable_reason="Запит розпізнано як набір випадкових символів, а не навчальну тему.",
        )

    # 3. Перевірка на типові побутові справи
    for pattern in NON_STUDY_PATTERNS:
        if re.search(pattern, lowered_topic):
            return PreparedInput(
                criteria=PlanCriteria(cleaned_topic, raw.available_minutes, raw.difficulty, cleaned_context),
                key_focus_terms=focus_terms,
                is_suitable_for_study=False,
                unsuitable_reason="Запит схожий на побутове доручення, а не навчальну тему. Спробуйте вказати предмет або навичку.",
            )

    return PreparedInput(
        criteria=PlanCriteria(
            topic=cleaned_topic,
            available_minutes=raw.available_minutes,
            difficulty=raw.difficulty,
            study_context=cleaned_context,
        ),
        key_focus_terms=focus_terms,
        is_suitable_for_study=True,
    )