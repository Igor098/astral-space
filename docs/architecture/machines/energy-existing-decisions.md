# Ранее согласованные решения по энергетической системе

**Статус:** навигационный документ; актуальные решения поддерживаются в одном каноническом ADR.  
**Актуализировано:** 2026-11-24.

Полный актуальный текст `ADR-ENERGY-001`, `ADR-ENERGY-002`, статус `TECH-DEBT-ENERGY-001` и описание действующей модели находятся в:

**[Astral Space — архитектурные решения по энергетической системе](../energy/AstralSpace_Energy_Architecture_Decisions.md)**.

Исторически этот файл содержал буквальную копию первоначального ADR. Чтобы избежать двух расходящихся версий после миграции на `EnergyPortId`, здесь оставлена ссылка на **единственный источник истины**.

Краткий статус:

- Режимы `INPUT`, `OUTPUT`, `DISABLED` утверждены; `BOTH` для FE отсутствует.
- `EnergyDistributionManager` уже идентифицирует порты по `EnergyPortId`, а не `Direction`.
- `EnergyComponent` реализован как внутреннее хранилище; координация с бюджетами и портами реализована в Coal через `EnergyTransferCoordinator`. Следующий архитектурный этап — независимые `EnergyInputController` / `EnergyOutputController` и тонкий `EnergyPortDispatcher`; это согласованные, ещё не реализованные классы. См. [AS-MACHINE-023](decisions/AS-MACHINE-023.md) и [AS-MACHINE-027](decisions/AS-MACHINE-027.md).
- `TECH-DEBT-ENERGY-001` закрыт для распределителя и выходов Coal Generator Mk I; физическая интеграция хэтчей мультиблоков ещё впереди.

Связанные материалы: [FE-система](energy-system.md), [план внедрения](implementation-plan.md), [реестр вопросов](open-questions.md), [AS-MACHINE-003](decisions/AS-MACHINE-003.md).
