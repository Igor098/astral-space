package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Фабрика компонентов управления режимами
 * энергетических портов.
 *
 * <p>Создаёт реализацию {@link EnergyPortPolicy}
 * в соответствии с декларативными правилами
 * {@link EnergyPortRules}.</p>
 *
 * <p>Для фиксированной конфигурации создаёт только
 * {@link FixedEnergyPortPolicy}. Для настраиваемой
 * конфигурации создаёт {@link EnergyPortConfiguration}
 * и связанную с ней {@link ConfigurableEnergyPortPolicy}.</p>
 *
 * <p>Каждый вызов фабрики создаёт независимый набор
 * компонентов. Фабрика не хранит изменяемое состояние
 * и не кэширует созданные экземпляры.</p>
 *
 * <p>Фабрика не зависит от игрового мира, конкретного
 * типа механизма и направления передачи энергии.</p>
 */
public final class EnergyPortPolicyFactory {
    private EnergyPortPolicyFactory() {}

    /**
     * Результат создания компонентов управления
     * энергетическими портами.
     *
     * <p>Содержит обязательную энергетическую политику
     * и необязательную изменяемую конфигурацию портов.</p>
     *
     * <p>При создании посредством фабрики для фиксированных
     * портов конфигурация отсутствует, а для настраиваемых
     * портов политика связана с возвращаемым экземпляром
     * конфигурации.</p>
     *
     * @param policy        энергетическая политика
     * @param configuration изменяемая конфигурация портов
     *                      или {@code null}, если она отсутствует
     */
    public record Result(
        @NonNull EnergyPortPolicy policy, @Nullable EnergyPortConfiguration configuration
    )
    {
        /**
         * Создаёт результат сборки компонентов управления
         * энергетическими портами.
         *
         * @throws NullPointerException если policy равен
         *                              {@code null}
         */
        public Result {
            Objects.requireNonNull(policy, "policy cannot be null");
        }
    }

    /**
     * Создаёт компоненты управления энергетическими
     * портами на основании декларативных правил.
     *
     * <p>Для {@link EnergyPortRules.ConfigurationType#FIXED}
     * создаёт {@link FixedEnergyPortPolicy} с режимами
     * по умолчанию. Изменяемая конфигурация отсутствует.</p>
     *
     * <p>Для {@link EnergyPortRules.ConfigurationType#CONFIGURABLE}
     * создаёт {@link EnergyPortConfiguration} с режимами
     * по умолчанию и {@link ConfigurableEnergyPortPolicy},
     * использующую разрешённые режимы и актуальное
     * состояние созданной конфигурации.</p>
     *
     * <p>Политика использует переданный предикат для
     * динамической проверки доступности портов.</p>
     *
     * <p>При создании компонентов предикат доступности
     * и callback изменения конфигурации не вызываются.</p>
     *
     * <p>Callback изменения конфигурации обязателен
     * независимо от типа правил, однако для фиксированной
     * конфигурации не используется.</p>
     *
     * <p>При фактическом изменении режима настраиваемого
     * порта callback получает идентификатор этого порта.
     * Уведомление выполняется после сохранения нового
     * режима в созданной конфигурации.</p>
     *
     * <p>Каждый вызов создаёт новые независимые экземпляры
     * компонентов без кэширования и взаимодействия
     * с игровым миром.</p>
     *
     * @param rules                  декларативные правила
     *                               энергетических портов
     * @param availability           предикат динамической
     *                               доступности порта
     * @param onConfigurationChanged обработчик фактического
     *                               изменения пользовательской
     *                               конфигурации, принимающий
     *                               идентификатор изменённого
     *                               энергетического порта
     * @return результат создания компонентов управления
     * энергетическими портами
     * @throws NullPointerException если хотя бы один
     *                              аргумент равен {@code null}
     */
    public static @NonNull Result create(
        @NonNull EnergyPortRules rules,
        @NonNull Predicate<EnergyPortId> availability,
        @NonNull Consumer<EnergyPortId> onConfigurationChanged
    )
    {
        Objects.requireNonNull(rules, "rules cannot be null");
        Objects.requireNonNull(availability, "availability cannot be null");
        Objects.requireNonNull(onConfigurationChanged, "onConfigurationChanged cannot be null");

        return switch (rules.configurationType()) {
            case FIXED -> new Result(
                new FixedEnergyPortPolicy(rules.defaultModes(), availability),
                null
            );
            case CONFIGURABLE -> {
                EnergyPortConfiguration configuration =
                    new EnergyPortConfiguration(rules.defaultModes(), onConfigurationChanged);

                ConfigurableEnergyPortPolicy policy =
                    new ConfigurableEnergyPortPolicy(
                        rules.allowedModes(),
                        configuration::getMode,
                        availability
                    );

                yield new Result(policy, configuration);
            }
        };
    }
}
