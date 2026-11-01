package ru.greatdragonx.astralspace.common.energy.port.serialization;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortConfiguration;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortId;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortMode;
import ru.greatdragonx.astralspace.common.energy.port.EnergyPortPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Сериализатор конфигурации энергетических портов.
 *
 * <p>Преобразует отображение идентификаторов энергетических
 * портов на их текущие режимы в формат сохранения Minecraft
 * через {@link ValueOutput} и {@link ValueInput}.</p>
 *
 * <p>Данные сохраняются в отдельной секции
 * {@code astralspace_energy_ports}, содержащей версию
 * формата и список записей портов.</p>
 *
 * <p>При чтении неподдерживаемые версии формата
 * отклоняются, а некорректные записи отдельных
 * портов пропускаются.</p>
 *
 * <p>Сериализатор не проверяет, разрешён ли режим
 * конкретному механизму. За проверку ограничений
 * отвечает {@link EnergyPortPolicy}, используемая
 * при восстановлении через {@link EnergyPortConfiguration}.</p>
 *
 * <p>Класс не хранит состояние и предоставляет
 * только статические операции чтения и записи.</p>
 */
public final class EnergyPortConfigurationSerializer {
    private static final int FORMAT_VERSION = 1;

    private EnergyPortConfigurationSerializer() {
    }

    /**
     * Записывает конфигурацию энергетических портов
     * в предоставленный {@link ValueOutput}.
     *
     * <p>Данные сохраняются в секции
     * {@code astralspace_energy_ports}, содержащей
     * версию формата и список энергетических портов.</p>
     *
     * <p>Каждая запись содержит строковый идентификатор
     * порта и имя его текущего {@link EnergyPortMode}.</p>
     *
     * <p>Перед началом записи создаётся проверенная
     * неизменяемая копия конфигурации.
     * Исходная карта не изменяется.</p>
     *
     * <p>Порядок записей в сохраняемом списке
     * не гарантируется.</p>
     *
     * @param output выходной объект для записи данных
     * @param modes  конфигурация режимов энергетических портов
     * @throws NullPointerException если любой аргумент
     *                              равен {@code null}
     *                              либо карта содержит
     *                              {@code null} в ключах
     *                              или значениях
     */
    public static void write(
        @NonNull ValueOutput output,
        @NonNull Map<EnergyPortId, EnergyPortMode> modes
    )
    {
        Objects.requireNonNull(
            output,
            "output"
        );
        Map<EnergyPortId, EnergyPortMode> validatedModes = Map.copyOf(Objects.requireNonNull(
            modes,
            "modes cannot be null"
        ));

        ValueOutput root = output.child("astralspace_energy_ports");
        root.putInt(
            "version",
            FORMAT_VERSION
        );

        ValueOutput.ValueOutputList ports = root.childrenList("ports");

        for (Map.Entry<EnergyPortId, EnergyPortMode> entry : validatedModes.entrySet()) {
            ValueOutput record = ports.addChild();

            record.putString(
                "id",
                entry.getKey()
                    .value()
            );
            record.putString(
                "mode",
                entry.getValue()
                    .name()
            );
        }
    }

    /**
     * Восстанавливает режимы энергетических портов
     * из предоставленного {@link ValueInput}.
     *
     * <p>Поддерживается версия формата {@code 1}.
     * При отсутствии секции или неподдерживаемой версии
     * возвращается пустая карта.</p>
     *
     * <p>Некорректные идентификаторы и неизвестные режимы
     * пропускаются без прерывания чтения остальных записей.
     * Если идентификатор повторяется, сохраняется первая
     * корректная запись.</p>
     *
     * <p>Метод не проверяет допустимость режимов для
     * конкретного механизма. Такая проверка выполняется
     * при последующем восстановлении конфигурации
     * через {@link EnergyPortConfiguration#restoreModes(
     *Map, EnergyPortPolicy)}.</p>
     *
     * @param input входной объект с сохранёнными данными
     * @return неизменяемая карта восстановленных режимов,
     * возможно пустая
     * @throws NullPointerException если {@code input}
     *                              равен {@code null}
     */
    public static @NonNull @Unmodifiable Map<EnergyPortId, EnergyPortMode> read(
        @NonNull ValueInput input
    )
    {
        Objects.requireNonNull(
            input,
            "input cannot be null"
        );
        ValueInput root = input.childOrEmpty("astralspace_energy_ports");
        Map<EnergyPortId, EnergyPortMode> restoredModes = new HashMap<>();

        int version = root.getIntOr(
            "version",
            -1
        );

        if (version != FORMAT_VERSION) {
            return Collections.emptyMap();
        }

        ValueInput.ValueInputList ports = root.childrenListOrEmpty("ports");
        if (ports.isEmpty()) {
            return Collections.emptyMap();
        }

        for (ValueInput port : ports) {
            String id = port.getStringOr(
                "id",
                ""
            );
            String mode = port.getStringOr(
                "mode",
                ""
            );

            try {
                EnergyPortId portId = new EnergyPortId(id);
                EnergyPortMode portMode = EnergyPortMode.valueOf(mode);

                restoredModes.putIfAbsent(
                    portId,
                    portMode
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        return Map.copyOf(restoredModes);
    }
}
