package ru.greatdragonx.astralspace.common.energy.port;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Set;

/**
 * Неизменяемое описание правил отдельного
 * логического энергетического порта.
 *
 * <p>Определяет режим порта по умолчанию
 * и множество разрешённых режимов.</p>
 *
 * <p>Множество разрешённых режимов не может
 * быть пустым и обязательно содержит
 * режим по умолчанию.</p>
 *
 * <p>Переданное множество защитно копируется
 * и предоставляется в неизменяемом виде.</p>
 *
 * <p>Правило не определяет физическое расположение
 * порта, его текущий режим, физическую доступность
 * или возможность изменения конфигурации.</p>
 *
 * @param portId       идентификатор логического порта
 * @param defaultMode  режим порта по умолчанию
 * @param allowedModes множество разрешённых режимов
 */
public record EnergyPortRule(
    @NonNull EnergyPortId portId,
    @NonNull EnergyPortMode defaultMode,
    @NonNull Set<EnergyPortMode> allowedModes
)
{
    /**
     * Создаёт неизменяемое правило энергетического
     * порта и проверяет его инварианты.
     *
     * @throws NullPointerException     если один из аргументов
     *                                  или элемент множества
     *                                  равен {@code null}
     * @throws IllegalArgumentException если множество
     *                                  разрешённых режимов
     *                                  пустое или не содержит
     *                                  режим по умолчанию
     */
    public EnergyPortRule {
        Objects.requireNonNull(portId, "portId cannot be null");
        Objects.requireNonNull(defaultMode, "defaultMode cannot be null");
        allowedModes = Set.copyOf(Objects.requireNonNull(
            allowedModes,
            "allowedModes cannot be null"
        ));

        if (allowedModes.isEmpty()) {
            throw new IllegalArgumentException("allowedModes cannot be empty");
        }

        if (!allowedModes.contains(defaultMode)) {
            throw new IllegalArgumentException("defaultMode must be contained in allowedModes");
        }
    }

    /**
     * Проверяет, входит ли указанный режим
     * в множество разрешённых режимов порта.
     *
     * <p>Не учитывает текущий режим, физическую
     * доступность порта или возможность изменения
     * его конфигурации.</p>
     *
     * @param mode проверяемый режим
     * @return {@code true}, если режим разрешён
     * декларативным правилом порта
     * @throws NullPointerException если mode равен
     *                              {@code null}
     */
    public boolean allowsMode(@NonNull EnergyPortMode mode) {
        Objects.requireNonNull(mode, "mode cannot be null");

        return this.allowedModes.contains(mode);
    }
}
