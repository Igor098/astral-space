package ru.greatdragonx.astralspace.common.energy.generation;

import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.energy.port.*;

import java.util.Objects;
import java.util.Set;

/**
 * Неизменяемая декларативная конфигурация генератора.
 *
 * <p>Объединяет характеристики хранения, производства
 * и внешней выдачи энергии, правила логических портов
 * и физическую топологию точек подключения.</p>
 *
 * <p>Гарантирует согласованность идентификаторов
 * портов между правилами и топологией, а также
 * отсутствие режима {@link EnergyPortMode#INPUT}
 * среди разрешённых режимов генератора.</p>
 *
 * <p>Не содержит изменяемых пользовательских
 * настроек, текущего состояния энергетического
 * буфера и информации о runtime-доступности портов.</p>
 *
 * <p>Не определяет характеристики топлива, условия
 * запуска производственного процесса и фактическую
 * выработку энергии.</p>
 *
 * @param energy    спецификация хранения, производства
 *                  и внешней выдачи энергии генератора
 * @param portRules декларативные правила
 *                  логических энергетических портов
 * @param topology  физическая топология
 *                  энергетических портов
 */
public record GeneratorConfiguration(
    @NonNull GeneratorEnergySpec energy,
    @NonNull EnergyPortRules portRules,
    @NonNull EnergyPortTopology topology
)
{
    /**
     * Создаёт согласованную неизменяемую
     * конфигурацию генератора.
     *
     * <p>Множества идентификаторов логических портов
     * в правилах и физической топологии должны
     * совпадать независимо от порядка объявления.</p>
     *
     * <p>Для энергетических портов генератора
     * допускаются только режимы
     * {@link EnergyPortMode#OUTPUT} и
     * {@link EnergyPortMode#DISABLED}.</p>
     *
     * <p>Поддерживаются фиксированные и настраиваемые
     * конфигурации портов. Пустая конфигурация
     * допускается.</p>
     *
     * @throws NullPointerException     если любой
     *                                  компонент равен
     *                                  {@code null}
     * @throws IllegalArgumentException если множества
     *                                  идентификаторов
     *                                  портов не совпадают
     *                                  или хотя бы один
     *                                  порт допускает
     *                                  режим INPUT
     */
    public GeneratorConfiguration {
        Objects.requireNonNull(energy, "energy cannot be null");
        Objects.requireNonNull(portRules, "portRules cannot be null");
        Objects.requireNonNull(topology, "topology cannot be null");

        Set<EnergyPortId> ruleIds = Set.copyOf(portRules.portIds());
        Set<EnergyPortId> topologyIds = Set.copyOf(topology.portIds());
        if (!ruleIds.equals(topologyIds)) {
            throw new IllegalArgumentException(
                "Energy port rules and topology must contain the same port IDs: rules=" +
                    ruleIds +
                    ", topology=" +
                    topologyIds);
        }

        for (EnergyPortRule rule : portRules.rules()) {
            if (rule.allowsMode(EnergyPortMode.INPUT)) {
                throw new IllegalArgumentException("Generator energy port cannot allow INPUT mode: " +
                    rule.portId().value());
            }
        }
    }
}
