package ru.greatdragonx.astralspace.common.energy;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.AstralSpace;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;

import java.util.*;

/**
 * Управляет распределением исходящей энергии между логическими портами одного механизма.
 *
 * <p>Порты идентифицируются через {@link EnergyPortId}, а не через физические направления
 * блока. Список участников и порядок их обхода задаёт {@link #updateAvailablePorts(List)}.
 * Менеджер учитывает Pull-запросы, наблюдаемую Push-доступность, объём фактической передачи
 * и накопленные кредиты справедливости ({@code fairnessCredits}).</p>
 *
 * <p>Базовые квоты защищают долю активных потребителей. Адаптивная квота позволяет
 * использовать невостребованную энергию других портов с поправкой на результаты
 * предыдущего тика. История хранится по игровым тикам; устаревшие данные не считаются
 * спросом или передачей за предыдущий тик.</p>
 *
 * <p>Менеджер <strong>не передаёт энергию самостоятельно</strong> и не списывает её
 * из хранилища. Вызывающий код обеспечивает фактическую передачу и общий лимит FE/t,
 * передаёт актуальный остаток бюджета и регистрирует успешную передачу через
 * {@link #recordExtraction(EnergyPortId, int, long, TransactionContext)}.</p>
 *
 * <p>Изменения истории Pull-запросов, извлечения и кредитов справедливости
 * поддерживают откат через NeoForge {@link SnapshotJournal}. Изменение набора портов
 * и обновление Push-наблюдений <strong>не транзакционны</strong>: вызывайте их вне
 * открытых транзакций передачи энергии. Класс предназначен для последовательного
 * использования в контексте одного механизма, а не для многопоточного доступа.</p>
 *
 * @see EnergyPortId
 */
public final class EnergyDistributionManager {
    private static final long ACTIVE_WINDOW_TICKS = 20L;

    private final Map<EnergyPortId, Long> lastRequestTicks = new LinkedHashMap<>();
    private final Map<EnergyPortId, Integer> extractedByPort = new LinkedHashMap<>();
    private final Map<EnergyPortId, Integer> maxRequestedByPort = new LinkedHashMap<>();
    private final Map<EnergyPortId, Integer> previousExtractedByPort = new LinkedHashMap<>();
    private final Map<EnergyPortId, Integer> previousMaxRequestedByPort = new LinkedHashMap<>();
    private final Map<EnergyPortId, Long> fairnessCredits = new LinkedHashMap<>();
    private final Map<EnergyPortId, Long> activeSinceTicks = new LinkedHashMap<>();
    private final Map<EnergyPortId, Integer> pushAvailabilityByPort = new LinkedHashMap<>();
    private final Map<EnergyPortId, Integer> previousPushAvailabilityByPort = new LinkedHashMap<>();

    private long lastUsageTick = -1L;
    private long lastDemandTick = -1L;
    private long previousUsageTick = -1L;
    private long previousDemandTick = -1L;
    private long lastFairnessUpdateTick = -1L;
    private long lastPushAvailabilityTick = -1L;
    private long previousPushAvailabilityTick = -1L;

    private List<EnergyPortId> availablePorts = Collections.emptyList();

    private final int maxTransferPerTick;

    /**
     * Снимок учёта фактических передач за текущий и предыдущий тики.
     */
    private record UsageSnapshot(
        long tick,
        long previousTick,
        Map<EnergyPortId, Integer> extracted,
        Map<EnergyPortId, Integer> previousExtracted
    ) {}

    /**
     * Снимок Pull-запросов, активности портов и истории максимального спроса.
     */
    private record DemandSnapshot(
        long lastDemandTick,
        long previousDemandTick,
        Map<EnergyPortId, Long> lastRequestTicks,
        Map<EnergyPortId, Long> activeSinceTicks,
        Map<EnergyPortId, Integer> maxRequestedByPort,
        Map<EnergyPortId, Integer> previousMaxRequestedByPort
    ) {}

    /**
     * Снимок кредитов справедливости и тика последнего пересчёта.
     */
    private record FairnessSnapshot(
        long lastFairnessUpdateTick, Map<EnergyPortId, Long> fairnessCredits
    ) {}

    private final SnapshotJournal<UsageSnapshot> journal = new SnapshotJournal<>() {
        /** Сохраняет значения счётчиков и карты фактической передачи для отката. */
        @Override
        protected @NonNull UsageSnapshot createSnapshot() {
            return new UsageSnapshot(
                EnergyDistributionManager.this.lastUsageTick,
                EnergyDistributionManager.this.previousUsageTick,
                new LinkedHashMap<>(EnergyDistributionManager.this.extractedByPort),
                new LinkedHashMap<>(EnergyDistributionManager.this.previousExtractedByPort)
            );
        }

        /** Восстанавливает состояние учёта фактической передачи из снимка. */
        @Override
        protected void revertToSnapshot(@NonNull UsageSnapshot snapshot) {
            EnergyDistributionManager.this.lastUsageTick = snapshot.tick();
            EnergyDistributionManager.this.previousUsageTick = snapshot.previousTick();
            EnergyDistributionManager.this.extractedByPort.clear();
            EnergyDistributionManager.this.previousExtractedByPort.clear();
            EnergyDistributionManager.this.extractedByPort.putAll(snapshot.extracted());
            EnergyDistributionManager.this.previousExtractedByPort.putAll(snapshot.previousExtracted());
        }
    };

    private final SnapshotJournal<DemandSnapshot> demandJournal = new SnapshotJournal<>() {
        /** Сохраняет историю Pull-запросов и активности портов для отката. */
        @Override
        protected @NonNull DemandSnapshot createSnapshot() {
            return new DemandSnapshot(
                EnergyDistributionManager.this.lastDemandTick,
                EnergyDistributionManager.this.previousDemandTick,
                new LinkedHashMap<>(EnergyDistributionManager.this.lastRequestTicks),
                new LinkedHashMap<>(EnergyDistributionManager.this.activeSinceTicks),
                new LinkedHashMap<>(EnergyDistributionManager.this.maxRequestedByPort),
                new LinkedHashMap<>(EnergyDistributionManager.this.previousMaxRequestedByPort)
            );
        }

        /** Восстанавливает историю Pull-запросов и активности из снимка. */
        @Override
        protected void revertToSnapshot(@NonNull DemandSnapshot snapshot) {
            EnergyDistributionManager.this.lastDemandTick = snapshot.lastDemandTick();
            EnergyDistributionManager.this.previousDemandTick = snapshot.previousDemandTick();
            EnergyDistributionManager.this.lastRequestTicks.clear();
            EnergyDistributionManager.this.activeSinceTicks.clear();
            EnergyDistributionManager.this.maxRequestedByPort.clear();
            EnergyDistributionManager.this.previousMaxRequestedByPort.clear();
            EnergyDistributionManager.this.lastRequestTicks.putAll(snapshot.lastRequestTicks());
            EnergyDistributionManager.this.activeSinceTicks.putAll(snapshot.activeSinceTicks());
            EnergyDistributionManager.this.maxRequestedByPort.putAll(snapshot.maxRequestedByPort());
            EnergyDistributionManager.this.previousMaxRequestedByPort.putAll(snapshot.previousMaxRequestedByPort());
        }
    };

    private final SnapshotJournal<FairnessSnapshot> fairnessJournal = new SnapshotJournal<>() {

        /** Сохраняет кредиты справедливости и время их пересчёта для отката. */
        @Override
        protected @NonNull FairnessSnapshot createSnapshot() {
            return new FairnessSnapshot(
                EnergyDistributionManager.this.lastFairnessUpdateTick,
                new LinkedHashMap<>(EnergyDistributionManager.this.fairnessCredits)
            );
        }

        /** Восстанавливает кредиты справедливости и тик пересчёта из снимка. */
        @Override
        protected void revertToSnapshot(
            @NonNull FairnessSnapshot snapshot
        )
        {
            EnergyDistributionManager.this.lastFairnessUpdateTick =
                snapshot.lastFairnessUpdateTick();

            EnergyDistributionManager.this.fairnessCredits.clear();
            EnergyDistributionManager.this.fairnessCredits.putAll(snapshot.fairnessCredits());
        }
    };

    /**
     * Создаёт распределитель с заданной максимальной скоростью выдачи энергии.
     *
     * <p>Изначально доступных портов нет. До обработки запросов необходимо вызвать
     * {@link #updateAvailablePorts(List)}.</p>
     *
     * @param maxTransferPerTick максимальная суммарная скорость выдачи, FE/t;
     *                           вызывающий код должен передавать положительное значение
     */
    public EnergyDistributionManager(int maxTransferPerTick) {
        this.maxTransferPerTick = maxTransferPerTick;
    }

    /**
     * Задаёт упорядоченный список доступных логических энергетических портов.
     *
     * <p>Порты, исключённые из списка, теряют всю накопленную историю запросов,
     * передач, Push-доступности и кредитов справедливости. Для оставшихся портов
     * история сохраняется, даже если изменился порядок. Повторная передача того же
     * списка ничего не меняет; вновь добавленный порт начинает с чистой статистикой.</p>
     *
     * <p>Порядок списка важен для детерминированного распределения остаточных FE.
     * Метод не участвует в механизме транзакционных откатов: вызывайте его
     * <strong>только вне открытой транзакции</strong>.</p>
     *
     * @param portIds доступные идентификаторы в порядке распределения; без повторов
     * @throws NullPointerException     если список или какой-либо его элемент равен {@code null}
     * @throws IllegalArgumentException если один идентификатор встречается несколько раз
     */
    public void updateAvailablePorts(List<EnergyPortId> portIds) {
        Objects.requireNonNull(
            portIds,
            "portIds must not be null"
        );

        if (this.availablePorts.equals(portIds)) {
            return;
        }

        List<EnergyPortId> ports = List.copyOf(portIds);
        Set<EnergyPortId> uniquePorts = Set.copyOf(ports);
        boolean compositionChanged = !new HashSet<>(this.availablePorts).equals(uniquePorts);

        if (ports.size() != uniquePorts.size()) {
            throw new IllegalArgumentException("duplicated ports not allowed");
        }

        AstralSpace.LOGGER.debug(
            "[EnergyManager] PORTS BEFORE={} NEW={} FAIRNESS BEFORE={}",
            this.availablePorts,
            ports,
            this.fairnessCredits
        );

        this.lastRequestTicks.keySet()
            .retainAll(uniquePorts);
        this.extractedByPort.keySet()
            .retainAll(uniquePorts);
        this.maxRequestedByPort.keySet()
            .retainAll(uniquePorts);
        this.previousMaxRequestedByPort.keySet()
            .retainAll(uniquePorts);
        this.previousExtractedByPort.keySet()
            .retainAll(uniquePorts);
        this.fairnessCredits.keySet()
            .retainAll(uniquePorts);
        this.activeSinceTicks.keySet()
            .retainAll(uniquePorts);
        this.pushAvailabilityByPort.keySet()
            .retainAll(uniquePorts);
        this.previousPushAvailabilityByPort.keySet()
            .retainAll(uniquePorts);

        this.availablePorts = ports;

        if (compositionChanged) {
            this.normalizeFairnessCredits();
        }

        AstralSpace.LOGGER.debug(
            "[EnergyManager] PORTS AFTER={} FAIRNESS AFTER={}",
            this.availablePorts,
            this.fairnessCredits
        );
    }

    /**
     * Регистрирует положительный Pull-запрос порта в пределах текущего тика.
     *
     * <p>В рамках одного тика хранится <em>максимальный</em> размер отдельного запроса,
     * а не сумма всех запросов. Обновляется время последней активности порта.
     * Если порт не был активен, его прежний кредит справедливости сбрасывается
     * и фиксируется начало нового периода активности. Запросы недоступных портов
     * и неположительные значения игнорируются.</p>
     *
     * <p>Все изменения учитываются в транзакционных снимках и откатываются
     * при отмене {@code transaction}.</p>
     *
     * @param portId      идентификатор запрашивающего порта
     * @param requested   размер одного Pull-запроса, FE
     * @param currentTick текущий игровой тик
     * @param transaction контекст транзакции, к которой относится запрос
     */
    public void recordRequest(
        EnergyPortId portId,
        int requested,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (requested <= 0) {
            return;
        }

        if (!this.isPortAvailable(portId)) {
            return;
        }

        this.demandJournal.updateSnapshots(transaction);

        if (this.lastDemandTick != currentTick) {
            if (this.lastDemandTick == currentTick - 1) {
                this.previousDemandTick = this.lastDemandTick;
                this.previousMaxRequestedByPort.clear();
                this.previousMaxRequestedByPort.putAll(this.maxRequestedByPort);
            } else {
                this.previousDemandTick = currentTick - 1;
                this.previousMaxRequestedByPort.clear();
            }
            this.lastDemandTick = currentTick;
            this.maxRequestedByPort.clear();
        }

        if (!this.isActive(
            portId,
            currentTick
        ))
        {
            if (this.getPreviousPushAvailability(
                portId,
                currentTick
            ) <= 0)
            {
                this.fairnessJournal.updateSnapshots(transaction);
                this.fairnessCredits.remove(portId);
            }

            this.activeSinceTicks.put(
                portId,
                currentTick
            );
        }

        int maxRequested = this.maxRequestedByPort.getOrDefault(
            portId,
            0
        );

        this.maxRequestedByPort.put(
            portId,
            Math.max(
                maxRequested,
                requested
            )
        );

        this.lastRequestTicks.put(
            portId,
            currentTick
        );
    }

    /**
     * Регистрирует фактически переданную через порт энергию за текущий тик.
     *
     * <p>В отличие от Pull-спроса, объёмы успешных передач в одном тике
     * <em>суммируются</em>. При смене тика статистика переносится в историю;
     * после пропущенных тиков старая передача не считается передачей предыдущего тика.
     * Неположительное значение игнорируется.</p>
     *
     * <p>Метод учитывает уже состоявшуюся передачу, но не проверяет общий лимит FE/t
     * и не изменяет энергетическое хранилище. Запись транзакционна.</p>
     *
     * @param portId      порт, через который успешно передана энергия
     * @param extracted   фактически переданное количество, FE
     * @param currentTick текущий игровой тик
     * @param transaction контекст транзакции передачи энергии
     * @throws IllegalStateException если при положительном {@code extracted}
     *                               порт отсутствует в списке доступных
     */
    public void recordExtraction(
        EnergyPortId portId,
        int extracted,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (extracted <= 0) {
            return;
        }

        if (!this.isPortAvailable(portId)) {
            throw new IllegalStateException(
                "Cannot record energy extraction from unavailable port: " +
                    portId);
        }

        this.journal.updateSnapshots(transaction);

        if (this.lastUsageTick != currentTick) {
            if (this.lastUsageTick == currentTick - 1) {
                this.previousUsageTick = this.lastUsageTick;

                this.previousExtractedByPort.clear();
                this.previousExtractedByPort.putAll(this.extractedByPort);
            } else {
                this.previousExtractedByPort.clear();
                this.previousUsageTick = currentTick - 1;
            }

            this.lastUsageTick = currentTick;
            this.extractedByPort.clear();
        }

        int alreadyExtracted = this.getExtractedByPort(
            portId,
            currentTick
        );
        this.extractedByPort.put(
            portId,
            alreadyExtracted + extracted
        );
    }

    /**
     * Обновляет наблюдаемую возможность Push-передачи по доступным портам.
     *
     * <p>Карта описывает обнаруженную возможность отдать энергию соседним получателям,
     * а <strong>не подтверждённый объём фактической передачи</strong>. При каждом вызове
     * текущая карта заменяется целиком: отсутствующий порт означает нулевую доступность.
     * Неположительные значения и неизвестные порты игнорируются.</p>
     *
     * <p>При первом обновлении нового тика предыдущее наблюдение сохраняется
     * в истории. Повторное обновление в том же тике перезаписывает только текущие
     * данные, не стирая историю. Входная карта копируется и проверяется до изменения
     * состояния. Изменения Push-доступности не поддерживают транзакционный откат.</p>
     *
     * @param availability наблюдаемая Push-доступность каждого порта, FE
     * @param currentTick  тик наблюдения
     * @throws NullPointerException если карта, её ключ или значение равны {@code null}
     */
    public void updatePushAvailability(
        @NonNull Map<EnergyPortId, Integer> availability,
        long currentTick
    )
    {
        Map<EnergyPortId, Integer> snapshot = Map.copyOf(Objects.requireNonNull(
            availability,
            "availability cannot be null"
        ));

        if (this.lastPushAvailabilityTick != currentTick) {
            this.previousPushAvailabilityByPort.clear();
            this.previousPushAvailabilityByPort.putAll(this.pushAvailabilityByPort);

            this.previousPushAvailabilityTick = this.lastPushAvailabilityTick;
        }

        this.pushAvailabilityByPort.clear();

        for (Map.Entry<EnergyPortId, Integer> entry : snapshot.entrySet()) {
            EnergyPortId portId = entry.getKey();
            if (!this.isPortAvailable(portId)) {
                continue;
            }

            int available = entry.getValue();

            if (available <= 0) {
                continue;
            }

            this.pushAvailabilityByPort.put(
                portId,
                available
            );
        }

        this.lastPushAvailabilityTick = currentTick;
    }

    /**
     * Проверяет наличие недавнего положительного Pull-запроса порта.
     *
     * <p>Порт считается активным в тик запроса и следующие 19 тиков
     * (окно {@code ACTIVE_WINDOW_TICKS = 20}). Запрос из будущего не учитывается.
     * Push-доступность сама по себе не делает порт активным по этому критерию.</p>
     *
     * @param portId      проверяемый порт
     * @param currentTick тик проверки
     * @return {@code true}, если порт доступен и его последний Pull-запрос
     * находится в окне активности
     */
    public boolean isActive(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return false;
        }

        Long lastTick = this.lastRequestTicks.get(portId);
        if (lastTick == null) {
            return false;
        }

        long elapsed = currentTick - lastTick;
        if (elapsed < 0) {
            return false;
        }

        return elapsed < ACTIVE_WINDOW_TICKS;
    }

    /**
     * Подсчитывает доступные порты с недавними Pull-запросами.
     *
     * <p>Push-only порты без Pull-запросов в этот счётчик не входят.</p>
     *
     * @param currentTick тик проверки активности
     * @return количество активных по Pull портов
     */
    public int countActivePorts(long currentTick) {
        int count = 0;

        for (EnergyPortId portId : this.availablePorts) {
            if (this.isActive(
                portId,
                currentTick
            ))
            {
                count++;
            }
        }

        return count;
    }

    /**
     * Вычисляет базовую равную квоту активного Pull-порта.
     *
     * <p>Максимальная суммарная скорость выдачи ({@code maxTransferPerTick})
     * делится на число активных Pull-портов с округлением вниз. Объём фактически
     * накопленной энергии и Push-only участники здесь не учитываются.</p>
     *
     * @param portId      порт, для которого рассчитывается квота
     * @param currentTick текущий игровой тик
     * @return базовая квота в FE/t либо {@code 0} для неактивного порта
     */
    public int getBaseQuota(
        EnergyPortId portId,
        long currentTick
    )
    {
        boolean isActive = this.isActive(
            portId,
            currentTick
        );
        if (!isActive) {
            return 0;
        }

        int activePorts = this.countActivePorts(currentTick);

        return this.maxTransferPerTick / activePorts;
    }

    /**
     * Возвращает неиспользованный остаток базовой квоты Pull-порта.
     *
     * @param portId      порт-получатель
     * @param currentTick текущий игровой тик
     * @return {@code max(0, базовая квота - уже передано в текущем тике)}, FE
     */
    public int getRemainingBaseQuota(
        EnergyPortId portId,
        long currentTick
    )
    {
        int quota = this.getBaseQuota(
            portId,
            currentTick
        );

        int extracted = this.getExtractedByPort(
            portId,
            currentTick
        );

        int remains = quota - extracted;

        return Math.max(
            0,
            remains
        );
    }

    /**
     * Рассчитывает базовую долю фактически доступного объёма энергии
     * для одного подходящего Pull- или Push-порта.
     *
     * <p>В отличие от {@link #getBaseQuota(EnergyPortId, long)}, знаменатель
     * включает как Pull-активные порты, так и порты с положительной текущей
     * Push-доступностью, а числитель равен {@code effectiveSupply}.</p>
     *
     * @param portId          рассматриваемый порт
     * @param effectiveSupply объём энергии, принимаемый за основу распределения, FE
     * @param currentTick     текущий игровой тик
     * @return целочисленная базовая доля, FE; {@code 0} для неподходящего порта
     */
    private int getBaseQuotaForSupply(
        EnergyPortId portId,
        int effectiveSupply,
        long currentTick
    )
    {
        if (!this.isEligibleForQuota(
            portId,
            currentTick
        ))
        {
            return 0;
        }

        int eligiblePorts = this.countEligibleForQuota(currentTick);
        if (eligiblePorts == 0) {
            return 0;
        }

        return effectiveSupply / eligiblePorts;
    }

    /**
     * Ограничивает размер Pull-запроса неиспользованной базовой квотой порта.
     *
     * @param portId      запрашивающий порт
     * @param requested   запрошенное количество энергии, FE
     * @param currentTick текущий игровой тик
     * @return разрешённый объём в пределах базовой квоты, FE;
     * {@code 0} при неположительном запросе
     */
    public int getAllowedBaseAmount(
        EnergyPortId portId,
        int requested,
        long currentTick
    )
    {
        if (requested <= 0) {
            return 0;
        }

        int remainingQuota = this.getRemainingBaseQuota(
            portId,
            currentTick
        );

        return Math.min(
            remainingQuota,
            requested
        );
    }

    /**
     * Читает объём фактической передачи через порт в указанном тике.
     *
     * @param portId      идентификатор порта
     * @param currentTick тик, за который запрашиваются данные
     * @return суммарно переданное количество, FE; {@code 0}, если порт недоступен
     * или статистика текущего тика отсутствует
     */
    public int getExtractedByPort(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        if (this.lastUsageTick != currentTick) {
            return 0;
        }

        return this.extractedByPort.getOrDefault(
            portId,
            0
        );
    }

    /**
     * Возвращает фактическую передачу порта строго за тик {@code currentTick - 1}.
     *
     * <p>История читается как до, так и после появления записей текущего тика.
     * Более старые наблюдения не подставляются вместо данных предыдущего тика.</p>
     *
     * @param portId      идентификатор порта
     * @param currentTick текущий игровой тик
     * @return фактическая передача за предыдущий тик, FE, или {@code 0}
     */
    public int getPreviousExtractedByPort(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        if (this.lastUsageTick == currentTick - 1) {
            return this.extractedByPort.getOrDefault(
                portId,
                0
            );
        }

        if (this.previousUsageTick == currentTick - 1) {
            return this.previousExtractedByPort.getOrDefault(
                portId,
                0
            );
        }

        return 0;
    }

    /**
     * Читает максимальный отдельный Pull-запрос порта в текущем тике.
     *
     * @param portId      идентификатор порта
     * @param currentTick тик запрашиваемой статистики
     * @return максимум одного Pull-запроса, FE, либо {@code 0}
     * при отсутствии данных или недоступном порте
     */
    public int getMaxRequestedByPort(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        if (this.lastDemandTick != currentTick) {
            return 0;
        }

        return this.maxRequestedByPort.getOrDefault(
            portId,
            0
        );
    }

    /**
     * Читает максимальный отдельный Pull-запрос порта за предыдущий тик.
     *
     * <p>Не возвращает спрос за более ранние тики, даже если после него
     * новых Pull-запросов не было.</p>
     *
     * @param portId      идентификатор порта
     * @param currentTick текущий игровой тик
     * @return максимум Pull-запроса за {@code currentTick - 1}, FE, или {@code 0}
     */
    public int getPreviousMaxRequestedByPort(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        if (this.lastDemandTick == currentTick - 1) {
            return this.maxRequestedByPort.getOrDefault(
                portId,
                0
            );
        }

        if (this.previousDemandTick == currentTick - 1) {
            return this.previousMaxRequestedByPort.getOrDefault(
                portId,
                0
            );
        }

        return 0;
    }

    /**
     * Вычисляет ещё не использованную защищённую квоту конкретного порта.
     *
     * <p>За основу берётся равная доля {@code effectiveSupply} среди подходящих
     * Pull/Push-портов. Защищаемый объём не превосходит наблюдаемый спрос:
     * максимума предыдущего Pull-запроса, текущего Pull-запроса и текущей
     * Push-доступности. Если такой спрос равен нулю, используется базовая доля.
     * Из резерва вычитается энергия, уже переданная порту в текущем тике;
     * итог не может быть отрицательным.</p>
     *
     * @param portId          порт, долю которого необходимо защитить
     * @param effectiveSupply объём энергии для вычисления общей базовой доли, FE
     * @param currentTick     текущий игровой тик
     * @return защищённый остаток квоты, FE
     */
    public int getProtectedRemainingQuota(
        EnergyPortId portId,
        int effectiveSupply,
        long currentTick
    )
    {
        int baseQuota = this.getBaseQuotaForSupply(
            portId,
            effectiveSupply,
            currentTick
        );
        int previousMaxRequested = this.getPreviousMaxRequestedByPort(
            portId,
            currentTick
        );
        int currentMaxRequested = this.getMaxRequestedByPort(
            portId,
            currentTick
        );

        int pushAvailability = this.getPushAvailability(
            portId,
            currentTick
        );

        int maxRequested = Math.max(
            Math.max(
                previousMaxRequested,
                currentMaxRequested
            ),
            pushAvailability
        );

        int reserve;

        if (maxRequested == 0) {
            reserve = baseQuota;
        } else {
            reserve = Math.min(
                maxRequested,
                baseQuota
            );
        }

        int finalQuota = reserve - this.getExtractedByPort(
            portId,
            currentTick
        );

        return Math.max(
            0,
            finalQuota
        );
    }

    /**
     * Суммирует защищённые остатки всех доступных портов, кроме указанного.
     *
     * <p>Результат используется адаптивным распределением: текущий порт
     * не должен забирать энергию, зарезервированную для других потребителей.
     * Если {@code targetPortId} отсутствует среди доступных, суммируются
     * квоты всех доступных портов.</p>
     *
     * @param targetPortId    порт, исключаемый из суммы
     * @param effectiveSupply общий объём энергии для расчёта квот, FE
     * @param currentTick     текущий игровой тик
     * @return сумма защищённых остатков остальных портов, FE
     */
    public int getTotalProtectedRemainingForOtherPorts(
        EnergyPortId targetPortId,
        int effectiveSupply,
        long currentTick
    )
    {
        int totalProtected = 0;

        for (EnergyPortId portId : this.availablePorts) {
            if (portId.equals(targetPortId)) {
                continue;
            }

            totalProtected += this.getProtectedRemainingQuota(
                portId,
                effectiveSupply,
                currentTick
            );
        }

        return totalProtected;
    }

    /**
     * Рассчитывает допустимый объём передачи через порт по адаптивному алгоритму.
     *
     * <p>Перед расчётом однократно за тик обновляются кредиты справедливости.
     * Если активных Pull-портов не больше одного, накопленные кредиты очищаются.
     * Затем вычисляется доступный объём с учётом уже израсходованной части
     * общего лимита FE/t, текущего запаса энергии, защищённых квот других портов
     * и баланса кредитов рассматриваемого порта.</p>
     *
     * <p>Результат не превосходит {@code requested}, {@code storedEnergy} и
     * {@code remainingGlobalBudget}. Метод <strong>не выполняет передачу</strong>
     * и не регистрирует извлечение: после успешной передачи вызывающий код должен
     * вызвать {@link #recordExtraction(EnergyPortId, int, long, TransactionContext)}
     * в соответствующей транзакции. Обновление кредитов возможно даже при нулевом
     * запросе или исчерпанном бюджете; оно откатывается вместе с транзакцией.
     * Для недоступного порта метод сразу возвращает {@code 0} без изменений.</p>
     *
     * @param portId                порт, запрашивающий разрешение на передачу
     * @param requested             желаемый объём передачи, FE
     * @param remainingGlobalBudget ещё не израсходованный общий лимит выдачи, FE/t;
     *                              ожидается значение от {@code 0} до
     *                              {@code maxTransferPerTick} включительно
     * @param storedEnergy          доступная энергия в хранилище на момент вызова, FE;
     *                              ожидается неотрицательное значение
     * @param currentTick           текущий игровой тик
     * @param transaction           транзакция, в рамках которой пересчитываются кредиты
     * @return максимально допустимый объём передачи через порт, FE
     */
    public int getAllowedAdaptiveAmount(
        EnergyPortId portId,
        int requested,
        int remainingGlobalBudget,
        int storedEnergy,
        long currentTick,
        TransactionContext transaction
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        this.fairnessJournal.updateSnapshots(transaction);

        this.updateFairnessCredits(currentTick);
        if (this.countActivePorts(currentTick) <= 1 &&
            this.countEligibleForQuota(currentTick) <= 1 &&
            this.countDemandingPortsPreviousTick(currentTick) <= 1)
        {
            this.fairnessCredits.clear();
        }

        if (requested <= 0 || remainingGlobalBudget <= 0) {
            return 0;
        }

        int alreadyExtracted = this.maxTransferPerTick - remainingGlobalBudget;

        int effectiveSupply = Math.min(
            this.maxTransferPerTick,
            alreadyExtracted + storedEnergy
        );

        int maxLimit = Math.min(
            storedEnergy,
            remainingGlobalBudget
        );

        int protectedFromOtherPorts = this.getAdaptiveProtectedRemainingForOtherPorts(
            portId,
            effectiveSupply,
            maxLimit,
            currentTick
        );

        int allowed = maxLimit - protectedFromOtherPorts;

        return Math.min(
            allowed,
            requested
        );
    }

    /**
     * Читает положительную Push-доступность порта, наблюдаемую в указанном тике.
     *
     * @param portId      идентификатор порта
     * @param currentTick тик наблюдения
     * @return наблюдаемая возможность передачи, FE, или {@code 0}, если порт
     * недоступен либо наблюдение относится к другому тику
     */
    public int getPushAvailability(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        if (this.lastPushAvailabilityTick != currentTick) {
            return 0;
        }

        return this.pushAvailabilityByPort.getOrDefault(
            portId,
            0
        );
    }

    /**
     * Читает Push-доступность порта строго за предыдущий тик.
     *
     * <p>Значение доступно и до первого обновления текущего тика,
     * и после него. Пропущенные тики не заполняются устаревшими наблюдениями.</p>
     *
     * @param portId      идентификатор порта
     * @param currentTick текущий игровой тик
     * @return Push-доступность за {@code currentTick - 1}, FE, или {@code 0}
     */
    public int getPreviousPushAvailability(
        EnergyPortId portId,
        long currentTick
    )
    {
        if (!this.isPortAvailable(portId)) {
            return 0;
        }

        long targetTick = currentTick - 1;

        if (this.lastPushAvailabilityTick == targetTick) {
            return this.pushAvailabilityByPort.getOrDefault(
                portId,
                0
            );
        }

        if (this.previousPushAvailabilityTick == targetTick) {
            return this.previousPushAvailabilityByPort.getOrDefault(
                portId,
                0
            );
        }

        return 0;
    }

    /**
     * Проверяет принадлежность порта текущему набору доступных портов.
     *
     * @param portId проверяемый идентификатор
     * @return {@code true}, если идентификатор есть в {@code availablePorts}
     */
    private boolean isPortAvailable(EnergyPortId portId) {
        return this.availablePorts.contains(portId);
    }

    /**
     * Определяет, участвует ли порт в расчёте защищённых квот.
     *
     * <p>Подходит доступный порт с активным Pull-запросом или положительной
     * Push-доступностью текущего тика. Один порт учитывается не более одного раза.</p>
     *
     * @param portId      проверяемый порт
     * @param currentTick текущий игровой тик
     * @return {@code true}, если порт должен участвовать в распределении
     */
    private boolean isEligibleForQuota(
        EnergyPortId portId,
        long currentTick
    )
    {
        boolean isActive = this.isActive(
            portId,
            currentTick
        );
        int pushAvailability = this.getPushAvailability(
            portId,
            currentTick
        );

        int previousPushAvailability = this.getPreviousPushAvailability(
            portId,
            currentTick
        );

        return isActive ||
            pushAvailability > 0 ||
            (this.lastPushAvailabilityTick != currentTick && previousPushAvailability > 0);
    }

    /**
     * Подсчитывает доступные Pull/Push-порты, участвующие в расчёте квот.
     *
     * @param currentTick текущий игровой тик
     * @return число подходящих портов
     */
    private int countEligibleForQuota(
        long currentTick
    )
    {
        int count = 0;

        for (EnergyPortId portId : this.availablePorts) {
            if (this.isEligibleForQuota(
                portId,
                currentTick
            ))
            {
                count++;
            }
        }

        return count;
    }

    /**
     * Суммирует фактическую передачу всех доступных портов за предыдущий тик.
     *
     * @param currentTick текущий игровой тик
     * @return суммарно переданная за {@code currentTick - 1} энергия, FE
     */
    private int getTotalExtractedPreviousTick(long currentTick) {
        int total = 0;

        for (EnergyPortId portId : this.availablePorts) {
            total += this.getPreviousExtractedByPort(
                portId,
                currentTick
            );
        }

        return total;
    }

    /**
     * Оценивает подтверждённый спрос порта за предыдущий тик.
     *
     * <p>Берётся максимум из предыдущего Pull-запроса, фактической передачи
     * и предыдущей Push-доступности. Это позволяет учитывать потребителей,
     * которые не создавали явных Pull-запросов.</p>
     *
     * @param portId      идентификатор порта
     * @param currentTick текущий игровой тик
     * @return эффективный спрос за предыдущий тик, FE
     */
    private int getEffectivePreviousDemand(
        EnergyPortId portId,
        long currentTick
    )
    {
        return Math.max(
            Math.max(
                this.getPreviousMaxRequestedByPort(
                    portId,
                    currentTick
                ),
                this.getPreviousExtractedByPort(
                    portId,
                    currentTick
                )
            ),
            this.getPreviousPushAvailability(
                portId,
                currentTick
            )
        );
    }

    /**
     * Подсчитывает доступные порты с положительным эффективным спросом
     * в предыдущем тике.
     *
     * @param currentTick текущий игровой тик
     * @return количество портов с положительным предыдущим спросом
     */
    private int countDemandingPortsPreviousTick(long currentTick) {
        int count = 0;

        for (EnergyPortId portId : this.availablePorts) {
            int previousDemand = this.getEffectivePreviousDemand(
                portId,
                currentTick
            );

            if (previousDemand > 0) {
                count++;
            }
        }

        return count;
    }

    /**
     * Обновляет накопленные кредиты справедливости по результатам предыдущего тика.
     *
     * <p>Пересчёт выполняется не более одного раза за текущий тик. Если для порта
     * с положительным спросом недостаточно истории Pull-активности и не было
     * предыдущего Push-наблюдения, пересчёт прекращается. При отсутствии спроса
     * в предыдущем тике кредиты также не изменяются.</p>
     *
     * <p>Фактическая выдача прошлого тика делится между потребителями в виде
     * целевых долей. Доли, не востребованные малопотребляющими портами,
     * и остаток от целочисленного деления перераспределяются между остальными.
     * Для детерминированного выбора порядка обхода используется {@code availablePorts},
     * а стартовая позиция смещается по номеру предыдущего тика.</p>
     *
     * <p>Разница {@code targetShare - actualExtracted} добавляется к кредиту
     * порта. Баланс ограничивается диапазоном от {@code -maxTransferPerTick}
     * до {@code +maxTransferPerTick}. Транзакционный снимок здесь не создаётся:
     * вызывающий {@link #getAllowedAdaptiveAmount(EnergyPortId, int, int, int, long,
     * TransactionContext)} должен заранее зарегистрировать его в
     * {@code fairnessJournal}.</p>
     *
     * @param currentTick тик, в котором выполняется пересчёт
     */
    private void updateFairnessCredits(long currentTick) {
        if (this.lastFairnessUpdateTick == currentTick) {
            return;
        }

        long previousTick = currentTick - 1;
        this.lastFairnessUpdateTick = currentTick;

        for (EnergyPortId portId : this.availablePorts) {
            int previousDemand = this.getEffectivePreviousDemand(
                portId,
                currentTick
            );
            if (previousDemand <= 0) {
                continue;
            }

            boolean hadPreviousPushAvailability = this.getPreviousPushAvailability(
                portId,
                currentTick
            ) > 0;

            Long activeSince = this.activeSinceTicks.get(portId);

            if (!hadPreviousPushAvailability &&
                (activeSince == null || activeSince >= previousTick))
            {
                return;
            }
        }

        int countDemandingPorts = this.countDemandingPortsPreviousTick(currentTick);
        if (countDemandingPorts == 0) {
            return;
        }

        int totalEnergyExtracted = this.getTotalExtractedPreviousTick(currentTick);

        int fairShare = totalEnergyExtracted / countDemandingPorts;
        int unusedQuota = 0;

        for (EnergyPortId portId : this.availablePorts) {
            int demand = this.getEffectivePreviousDemand(
                portId,
                currentTick
            );

            if (demand <= 0) {
                continue;
            }

            unusedQuota += Math.max(
                0,
                fairShare - demand
            );
        }

        Map<EnergyPortId, Integer> targetShares = new LinkedHashMap<>();

        for (EnergyPortId portId : this.availablePorts) {
            int demand = this.getEffectivePreviousDemand(
                portId,
                currentTick
            );

            if (demand <= 0) {
                continue;
            }

            targetShares.put(
                portId,
                Math.min(
                    demand,
                    fairShare
                )
            );
        }

        int remainingToDistribute = unusedQuota + totalEnergyExtracted % countDemandingPorts;

        while (remainingToDistribute > 0) {
            List<EnergyPortId> eligiblePorts = new ArrayList<>();

            for (EnergyPortId portId : targetShares.keySet()) {
                int demand = this.getEffectivePreviousDemand(
                    portId,
                    currentTick
                );

                int currentShare = targetShares.get(portId);

                if (currentShare < demand) {
                    eligiblePorts.add(portId);
                }
            }

            if (eligiblePorts.isEmpty()) {
                break;
            }

            int portion = Math.max(
                1,
                remainingToDistribute / eligiblePorts.size()
            );

            int startIndex = Math.floorMod(
                previousTick,
                eligiblePorts.size()
            );

            for (int i = 0; i < eligiblePorts.size(); i++) {
                EnergyPortId portId = eligiblePorts.get((startIndex + i) % eligiblePorts.size());

                int demand = this.getEffectivePreviousDemand(
                    portId,
                    currentTick
                );

                int currentShare = targetShares.get(portId);

                int remainingDemand = demand - currentShare;
                int addition = Math.min(
                    portion,
                    Math.min(
                        remainingDemand,
                        remainingToDistribute
                    )
                );
                if (addition <= 0) {
                    continue;
                }

                targetShares.put(
                    portId,
                    currentShare + addition
                );
                remainingToDistribute -= addition;
            }
        }

        for (EnergyPortId portId : this.availablePorts) {
            int previousDemand = this.getEffectivePreviousDemand(
                portId,
                currentTick
            );

            if (previousDemand <= 0) {
                continue;
            }

            int actualExtracted = this.getPreviousExtractedByPort(
                portId,
                currentTick
            );

            int targetShare = targetShares.get(portId);
            int delta = targetShare - actualExtracted;

            long currentPortBalance = this.fairnessCredits.getOrDefault(
                portId,
                0L
            );

            long newBalance = Math.clamp(
                currentPortBalance + delta,
                (long) -this.maxTransferPerTick,
                this.maxTransferPerTick
            );

            this.fairnessCredits.put(
                portId,
                newBalance
            );
        }
    }


    private void normalizeFairnessCredits() {
        if (this.fairnessCredits.isEmpty()) {
            return;
        }

        long sum = 0L;

        for (long credit : this.fairnessCredits.values()) {
            sum += credit;
        }

        long limit = this.maxTransferPerTick;

        while (sum != 0L) {
            long correction = -sum;
            boolean increase = correction > 0L;

            List<EnergyPortId> eligible = new ArrayList<>();

            for (EnergyPortId portId : this.availablePorts) {
                Long credit = this.fairnessCredits.get(portId);

                if (credit == null) {
                    continue;
                }

                if ((increase && credit < limit) || (!increase && credit > -limit)) {
                    eligible.add(portId);
                }
            }

            if (eligible.isEmpty()) {
                throw new IllegalStateException("Unable to normalize fairness credits");
            }

            long portion = correction / eligible.size();
            long remainder = Math.abs(correction % eligible.size());

            for (int i = 0; i < eligible.size(); i++) {
                EnergyPortId portId = eligible.get(i);

                long current = this.fairnessCredits.get(portId);

                long adjustment = portion;

                if (i < remainder) {
                    adjustment += increase ? 1L : -1L;
                }

                long updated = Math.clamp(
                    current + adjustment,
                    -limit,
                    limit
                );

                this.fairnessCredits.put(
                    portId,
                    updated
                );

                sum += updated - current;
            }
        }
    }

    private int getAdaptiveProtectedRemainingForOtherPorts(
        EnergyPortId targetPort,
        int effectiveSupply,
        int availableNow,
        long currentTick
    )
    {
        int eligibleCount = this.countEligibleForQuota(currentTick);

        if (availableNow <= 0 || eligibleCount == 0) {
            return 0;
        }

        int baseQuota = effectiveSupply / eligibleCount;

        Map<EnergyPortId, Integer> reserves = new LinkedHashMap<>();

        long total = 0;
        int maximum = 0;

        for (EnergyPortId port : this.availablePorts) {
            if (!this.isEligibleForQuota(
                port,
                currentTick
            ))
            {
                continue;
            }

            int demand = Math.max(
                Math.max(
                    this.getPreviousMaxRequestedByPort(
                        port,
                        currentTick
                    ),
                    this.getMaxRequestedByPort(
                        port,
                        currentTick
                    )
                ),
                Math.max(
                    this.getPushAvailability(
                        port,
                        currentTick
                    ),
                    this.getPreviousPushAvailability(
                        port,
                        currentTick
                    )
                )
            );

            int target = Math.clamp(
                (long) baseQuota + this.fairnessCredits.getOrDefault(
                    port,
                    0L
                ),
                0,
                demand == 0 ? effectiveSupply : demand
            );

            int reserve = Math.max(
                0,
                target - this.getExtractedByPort(
                    port,
                    currentTick
                )
            );

            reserves.put(
                port,
                reserve
            );
            total += reserve;
            maximum = Math.max(
                maximum,
                reserve
            );
        }

        if (total > availableNow) {
            int reduction = this.getReduction(
                availableNow,
                maximum,
                reserves
            );
            List<EnergyPortId> remainderPorts = new ArrayList<>();

            total = 0;

            for (var entry : reserves.entrySet()) {
                int original = entry.getValue();

                if (original >= reduction) {
                    remainderPorts.add(entry.getKey());
                }

                int reduced = Math.max(
                    0,
                    original - reduction
                );
                entry.setValue(reduced);
                total += reduced;
            }

            int remainder = (int) (availableNow - total);
            int shift = Math.floorMod(
                currentTick,
                remainderPorts.size()
            );

            for (int i = 0; i < remainder; i++) {
                EnergyPortId port = remainderPorts.get((shift + i) % remainderPorts.size());

                reserves.put(
                    port,
                    reserves.get(port) + 1
                );
            }

            total = availableNow;
        }

        return (int) total - reserves.getOrDefault(
            targetPort,
            0
        );
    }

    private int getReduction(
        int availableNow,
        int maximum,
        Map<EnergyPortId, Integer> reserves
    )
    {
        int low = 0;
        int high = maximum;

        while (low < high) {
            int reduction = low + (high - low) / 2;
            long reducedTotal = 0;

            for (int reserve : reserves.values()) {
                reducedTotal += Math.max(
                    0,
                    reserve - reduction
                );
            }

            if (reducedTotal > availableNow) {
                low = reduction + 1;
            } else {
                high = reduction;
            }
        }

        return low;
    }

    public @NonNull @UnmodifiableView Map<EnergyPortId, Long> getFairnessCreditsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.fairnessCredits));
    }
}
