# Источники, область достоверности и технические ориентиры

## Источники проектных решений

1. Согласованные принципы AS-MACHINE-001–027, включая позднейшие уточнения FE-архитектуры, визуализации и ресурсного транспорта. См. [актуальный реестр ADR](README.md#реестр-решений). **Это проектный источник истины**, отдельные не решённые детали вынесены в `open-questions.md`.
2. [Канонический энергетический ADR](../energy/AstralSpace_Energy_Architecture_Decisions.md) (`ADR-ENERGY-001/002`, статус `TECH-DEBT-ENERGY-001`); прежний путь [energy-existing-decisions.md](energy-existing-decisions.md) сохранён как указатель, без повторного поддержания дублирующей копии.
3. Существующие Java-файлы `EnergyComponent`, `EnergyPortId`, политик, конфигурации, `EnergyDistributionManager`, `EnergyTransferBudget` и Coal/Lava Generator Mk I — источник **фактов о текущей реализации**, но **не** автоматическое утверждение нового универсального контракта. Интеграция обоих генераторов и оптимизированные снимки менеджера сверены с `main` после PR #3 (`3216128`); соответствие незапушенным локальным изменениям не утверждается.

## Официальная документация NeoForge

- [NeoForge 1.21.11: Capabilities](https://docs.neoforged.net/docs/1.21.11/inventories/capabilities/) — встроенные возможности `Capabilities.Item.BLOCK`, `.Fluid.BLOCK`, `.Energy.BLOCK`; контекст `Direction`; `BlockCapabilityCache`, инвалидация.
- [NeoForge: Transactions](https://docs.neoforged.net/docs/inventories/transactions/) — `ResourceHandler`, транзакционные операции, модель immutable/count-agnostic `Resource`; **перед применением API сверить страницу с актуальной версией 1.21.11 и исходниками**.

## Ограничения

- Встроенная газовая capability в официальном перечне NeoForge 1.21.11 отсутствует; способ интеграции газов требует **отдельного решения**.
- Наличие API транзакций само по себе не гарантирует атомарность сложной операции; её обеспечивает корректная транзакционная оркестрация наших хранилищ и сторонних обработчиков.
- Имена и точные Java-сигнатуры считаются согласованными только там, где соответствующий ADR прямо утверждает контракт (в частности, `GenerationProcess`, `PersistentGenerationProcess`, `FuelConsumption`, `FuelCycleComponent` и согласованный API `EnergyOutputController`). Примеры и рабочие имена, не утверждённые явно, не являются обязательными сигнатурами.
- Открытые вопросы и варианты не должны трактоваться как уже согласованные функциональные требования.


**Актуальная точка входа для целевой FE-архитектуры:** [общая генераторная база](decisions/AS-MACHINE-023.md), [топология портов](decisions/AS-MACHINE-024.md), [топливный процесс](decisions/AS-MACHINE-025.md), [хэтчи](decisions/AS-MACHINE-026.md), [Consumer и Storage](decisions/AS-MACHINE-027.md). Наличие нового ADR не означает реализации одноимённых классов в исходном коде.
