package ru.greatdragonx.astralspace.common.energy.port;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Хранит текущие настройки режимов энергетических портов
 * механизма.
 *
 * <p>Начальная конфигурация копируется при создании объекта.
 * Последующие изменения исходного словаря не влияют
 * на сохранённые режимы.</p>
 *
 * <p>Отсутствующие в конфигурации идентификаторы портов
 * возвращают {@link EnergyPortMode#DISABLED}.</p>
 *
 * <p>Изменение режимов допускается только после проверки
 * через {@link EnergyPortPolicy}. При фактическом изменении
 * вызывается переданный обработчик уведомления.</p>
 *
 * <p>Компонент не выполняет передачу энергии и не отвечает
 * непосредственно за сериализацию NBT или синхронизацию
 * с клиентом.</p>
 */
public final class EnergyPortConfiguration {
    private final Map<EnergyPortId, EnergyPortMode> currentModes;
    private final Runnable onConfigurationChanged;

    /**
     * Создаёт хранилище настроек энергетических портов.
     *
     * <p>Начальные режимы копируются во внутреннюю изменяемую
     * карту, недоступную вызывающему коду.</p>
     *
     * <p>Обработчик изменений сохраняется, но не вызывается
     * при создании конфигурации.</p>
     *
     * @param initialModes           начальные режимы энергетических портов
     * @param onConfigurationChanged обработчик изменения настроек
     * @throws NullPointerException если любой аргумент равен
     *                              {@code null} либо начальная
     *                              конфигурация содержит
     *                              {@code null} в ключах или значениях
     */
    public EnergyPortConfiguration(
        Map<EnergyPortId, EnergyPortMode> initialModes,
        Runnable onConfigurationChanged
    )
    {
        this.currentModes = new HashMap<>(Map.copyOf(Objects.requireNonNull(
            initialModes,
            "initialModes cannot be null"
        )));
        this.onConfigurationChanged = Objects.requireNonNull(
            onConfigurationChanged,
            "onConfigurationChanged cannot be null"
        );
    }


    /**
     * Возвращает текущий сохранённый режим энергетического порта.
     *
     * <p>Если указанный порт отсутствует во внутренней
     * конфигурации, возвращается {@link EnergyPortMode#DISABLED}.</p>
     *
     * <p>Метод только читает текущее состояние.
     * Он не проверяет допустимость режима через
     * {@link EnergyPortPolicy} и не учитывает физическую
     * доступность порта.</p>
     *
     * <p>Вызов метода не изменяет конфигурацию
     * и не вызывает обработчик уведомления.</p>
     *
     * @param portId идентификатор энергетического порта
     * @return сохранённый режим порта или
     * {@link EnergyPortMode#DISABLED},
     * если запись отсутствует
     * @throws NullPointerException если {@code portId}
     *                              равен {@code null}
     */
    public EnergyPortMode getMode(EnergyPortId portId) {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );

        return currentModes.getOrDefault(
            portId,
            EnergyPortMode.DISABLED
        );
    }


    /**
     * Создаёт неизменяемый снимок текущей конфигурации
     * энергетических портов.
     *
     * <p>Возвращаемый словарь содержит все записи,
     * сохранённые во внутренней конфигурации
     * на момент вызова метода.</p>
     *
     * <p>Снимок является независимой копией.
     * Последующие изменения конфигурации не влияют
     * на ранее созданные снимки.</p>
     *
     * <p>Изменение возвращённого словаря запрещено.
     * Попытка выполнить модифицирующую операцию
     * приводит к {@link UnsupportedOperationException}.</p>
     *
     * <p>Метод предназначен, в частности, для получения
     * состояния перед сериализацией и сохранением
     * настроек механизма.</p>
     *
     * @return неизменяемая независимая копия
     * текущих режимов энергетических портов
     */
    @Contract(pure = true)
    public @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortMode> snapshot() {
        return Map.copyOf(this.currentModes);
    }

    /**
     * Изменяет текущий режим энергетического порта,
     * если запрошенная настройка разрешена политикой.
     *
     * <p>Перед изменением проверяется допустимость нового
     * режима через {@link EnergyPortPolicy#canChangeMode(
     *EnergyPortId, EnergyPortMode)}.</p>
     *
     * <p>Если изменение запрещено либо запрошенный режим
     * уже установлен, состояние остаётся прежним,
     * а обработчик изменений не вызывается.</p>
     *
     * <p>При успешном изменении новый режим сохраняется,
     * после чего однократно вызывается обработчик
     * изменения конфигурации.</p>
     *
     * <p>Метод не выполняет сериализацию настроек
     * и не управляет напрямую Minecraft capabilities.</p>
     *
     * @param portId  идентификатор настраиваемого порта
     * @param newMode запрашиваемый режим порта
     * @param policy  политика, определяющая допустимость
     *                изменения режима
     * @return {@code true}, если режим был изменён;
     * {@code false}, если изменение запрещено
     * или режим уже установлен
     * @throws NullPointerException если {@code portId},
     *                              {@code newMode} или
     *                              {@code policy} равен
     *                              {@code null}
     */
    public boolean changeMode(
        EnergyPortId portId,
        EnergyPortMode newMode,
        @NonNull EnergyPortPolicy policy
    )
    {
        Objects.requireNonNull(
            portId,
            "portId cannot be null"
        );
        Objects.requireNonNull(
            newMode,
            "newMode cannot be null"
        );
        Objects.requireNonNull(
            policy,
            "policy cannot be null"
        );

        if (!policy.canChangeMode(
            portId,
            newMode
        ))
        {
            return false;
        }

        EnergyPortMode currentMode = this.getMode(portId);

        if (currentMode == newMode) {
            return false;
        }

        this.currentModes.put(
            portId,
            newMode
        );
        this.onConfigurationChanged.run();

        return true;
    }

    /**
     * Восстанавливает сохранённые режимы энергетических портов.
     *
     * <p>Каждый восстанавливаемый режим проверяется через
     * {@link EnergyPortPolicy#canChangeMode(
     *EnergyPortId, EnergyPortMode)}.
     * Неизвестные порты и запрещённые режимы игнорируются.</p>
     *
     * <p>Порты, отсутствующие в восстановленных данных,
     * сохраняют свои текущие настройки.</p>
     *
     * <p>Восстановление не вызывает обработчик изменения
     * конфигурации и не выполняет сериализацию NBT.</p>
     *
     * @param restoredModes восстановленные режимы портов
     * @param policy        политика проверки допустимых режимов
     * @throws NullPointerException если любой аргумент
     *                              равен {@code null}
     *                              либо карта содержит
     *                              {@code null} в ключах
     *                              или значениях
     */
    public void restoreModes(
        Map<EnergyPortId, EnergyPortMode> restoredModes,
        EnergyPortPolicy policy
    )
    {
        Objects.requireNonNull(
            policy,
            "policy cannot be null"
        );

        Map<EnergyPortId, EnergyPortMode> validatedModes = Map.copyOf(Objects.requireNonNull(
            restoredModes,
            "restoredModes cannot be null"
        ));

        for (Map.Entry<EnergyPortId, EnergyPortMode> entry : validatedModes.entrySet()) {
            Objects.requireNonNull(
                entry.getValue(),
                "restoredMode cannot be null"
            );

            if (!policy.canChangeMode(
                entry.getKey(),
                entry.getValue()
            ))
            {
                continue;
            }

            this.currentModes.put(
                entry.getKey(),
                entry.getValue()
            );
        }
    }
}
