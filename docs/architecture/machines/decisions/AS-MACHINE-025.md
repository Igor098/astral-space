# AS-MACHINE-025 — GenerationProcess и транзакционный топливный цикл генераторов

**Статус:** утверждено как целевая архитектура 2026-11-24; новые компоненты/интерфейсы и рефакторинг Coal/Lava пока не реализованы.  
**Область:** производственный процесс генераторов на предметном и жидкостном топливе.  
**Связанные решения:** [023](AS-MACHINE-023.md), [024](AS-MACHINE-024.md); [сохранение](../persistence-and-networking.md); [тесты](../test-matrix.md).

## Проблема и выбранное решение

Дублирование топливных `burnTime`/`burnDuration` и логики списания Coal/Lava устраняется **минимальной композицией со Strategy**, а не промежуточным `AbstractFuelCycleGeneratorBlockEntity` и не прямыми топливными хуками в базовом генераторе.

- `AbstractGeneratorBlockEntity` владеет общим FE-алгоритмом, хранит ссылку на `GenerationProcess` и **не содержит** топливных счётчиков.
- `FuelCycleGenerationProcess` управляет общим топливным циклом Coal/Lava, **владеет** одним `FuelCycleComponent` и получает узкое действие списания топлива.
- Coal предоставляет предметный источник и его правила, Lava — жидкостный. Ни общий процесс, ни `FuelCycleComponent` не получают напрямую `EnergyComponent`, `Level`, `BlockEntity` и FE-порты.
- Нетопливные процессы могут реализовывать ту же Strategy без фиктивного топливного компонента. Различия уровней Mk I/II/III с одинаковым алгоритмом задаются конфигурацией.

## Утверждённые Java-контракты

```java
interface GenerationProcess {
    boolean prepareProductionTick();
    void onProductionTickCompleted();
}

interface PersistentGenerationProcess extends GenerationProcess {
    void saveState(ValueOutput output);
    void restoreState(ValueInput input);
}

@FunctionalInterface
interface FuelConsumption {
    OptionalInt tryConsume(TransactionContext transaction);
}
```

- `prepareProductionTick()` вызывается **после** серверных проверок, достаточного места под *полную* порцию FE и редстоуна; разрешает продолжить оплаченный цикл либо пытается запустить новый. Сам не добавляет FE и не выполняет Push/Pull. `false` останавливает производственный тик.
- `onProductionTickCompleted()` вызывается **только после успешной полной записи FE**. Для топлива уменьшает остаток цикла на один тик.
- `PersistentGenerationProcess` **необязателен**: процесс без сохраняемого состояния не реализует его. Базовый генератор при наличии этого контракта делегирует сохранение/восстановление в `generation_process`.
- `FuelConsumption.tryConsume(tx)` работает в предоставленном контексте и возвращает **строго положительную** длительность только при полном успешном извлечении требуемого топлива; `OptionalInt.empty()` означает неуспех. Источник **не открывает и не коммитит собственную корневую транзакцию**. Не вводить иерархию `FuelSource`/адаптеры без доказанной необходимости; смысловой интерфейс предпочтительнее сырого `Function`.

## FuelCycleComponent

Согласован минимальный контракт:

```java
public FuelCycleComponent(Runnable onCycleChanged);

public boolean isActive();
public int getRemainingTicks();
public int getTotalTicks();
public void start(int durationTicks, TransactionContext transaction);
public void completeProductionTick();

public void serialize(ValueOutput output);
public void deserialize(ValueInput input);
```

- Конструктор требует ненулевой callback, устанавливает `remainingTicks=0` и `totalTicks=0`, **не вызывает callback** и не принимает `Level`, хранилище или начальную длительность.
- `isActive()` истинно ровно при `remainingTicks>0`.
- `start(duration, tx)`: `duration>0`, контекст обязателен; запуск **поверх активного** цикла запрещён (`IllegalStateException`). Неактивное завершённое `0 / total` допускает новый запуск. Компонент регистрирует **совместный снимок обоих int** через `SnapshotJournal`.
- `completeProductionTick()` — нетранзакционный декремент `remainingTicks` **ровно на 1**, только при активном цикле; вызов при нуле — ошибка. `totalTicks` при завершении может оставаться положительным для GUI.
- При старте callback вызывается **только после корневого commit и фактического изменения**, при завершении тика — сразу после изменения; при конструировании, rollback и NBT-восстановлении callbacks отсутствуют. Компонент сам не вызывает `setChanged()`.

## Одна транзакция на запуск цикла

`FuelCycleGenerationProcess` открывает **одну корневую NeoForge `Transaction`** для расхода конкретного Item/Fluid-топлива и `FuelCycleComponent.start(..., tx)`; подтверждает её только при полном успехе. При неуспехе или исключении до `commit` обе части должны откатиться. Счётчики нельзя присвоить обычным нетранзакционным способом между извлечением топлива и commit.

После открытия/подтверждения нового цикла сохраняется прежняя гарантия `FULL-TICK-OR-PAUSE`: расход одного производственного тика — **после** фактической полной записи FE. Однако сама запись произведённой FE через `EnergyComponent.storeProducedEnergy` **нетранзакционная**; атомарность всего тика (включая FE) настоящим решением **не обещается**. Исключительные сбои полной записи должны быть отдельно обработаны и протестированы.

## Создание и привязка процесса

`AbstractGeneratorBlockEntity` не может конструировать процесс, которому нужны ещё не созданные хранилища потомка. Поэтому:

1. Родитель создаёт общую FE-инфраструктуру.
2. Конкретный Coal/Lava создаёт свои топливные хранилища.
3. Конкретный генератор создаёт `FuelCycleComponent` и `FuelCycleGenerationProcess`.
4. Через **защищённый final-метод однократно привязывает** готовый `GenerationProcess` к базовому генератору.

Повторная привязка запрещена; отсутствие процесса при использовании — ошибка инициализации. Ссылка базового генератора может быть не `final` — принятый компромисс. Из родительского конструктора не вызывать переопределяемые методы, не обращаться к ещё не заданному `Level`, не вызывать callback компонентов. Точное имя метода привязки не утверждено.

## NBT и восстановление

Структура **нового единственного** формата (без миграции старых `burnTime`/`burnDuration`):

```text
generation_process
└── fuel_cycle
    ├── remaining_ticks: int
    └── total_ticks: int
```

- `AbstractGeneratorBlockEntity` создаёт `generation_process` и через `PersistentGenerationProcess.saveState/restoreState` делегирует управление. `FuelCycleGenerationProcess` создаёт `fuel_cycle`, затем вызывает `FuelCycleComponent.serialize/deserialize` с **готовой секцией**.
- Инвариант `0 <= remaining_ticks <= total_ticks`. Корректны пустое `0/0`, активное `remaining>0` и законченное `0/total`, где `total>0`.
- При отсутствии **всей** секции состояние `0/0`. При отсутствии хотя бы одного поля, неверном NBT-типе либо нарушении инварианта **весь цикл сбрасывается в `0/0`**; не исправлять повреждённое значение частичным clamp.
- `deserialize` читает каждое поле через `ValueInput.getInt(key)` (результат `Optional<Integer>`), **не** `getIntOr(key, 0)`: отсутствующий ключ нельзя ошибочно признать корректным нулём.
- `loadAdditional` восстанавливает **тихо**, без `Transaction`, `SnapshotJournal`, callback, `setChanged()`, сети и мира. До `loadAdditional` все компоненты созданы и процесс привязан. `onLoad` затем выполняет идемпотентную серверную синхронизацию.

## Не путать целевую модель с реализованной

В текущем `feature/lava-generator-mk1` Lava извлекает жидкость в транзакции, но обычным присваиванием задаёт `burnTime/burnDuration`; Coal подтверждает транзакцию списания предмета отдельно от изменения времени цикла. Эти схемы **должны быть заменены при реализации**, но сам документационный ADR код не меняет.

Сигнатуры и правила выше **согласованы**; Java-пакеты, конкретный вид снимка `SnapshotJournal`, сообщения исключений и unit/integration тесты — детали реализации.
