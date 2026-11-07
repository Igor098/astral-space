package ru.greatdragonx.astralspace.common.machine.visual.layout;

import org.jspecify.annotations.NonNull;
import ru.greatdragonx.astralspace.common.machine.MachineSide;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPort;
import ru.greatdragonx.astralspace.common.machine.visual.VisualPortId;

import java.util.*;

public class PortLayout {
    private static final PortLayoutPreset CENTER = new PortLayoutPreset(
        0.0f,
        0.0f,
        1.0f
    );
    private static final PortLayoutPreset UPPER = new PortLayoutPreset(
        0.0f,
        0.1375f,
        0.6f
    );
    private static final PortLayoutPreset LOWER = new PortLayoutPreset(
        0.0f,
        -0.1375f,
        0.6f
    );

    public List<PortPlacement> arrange(@NonNull List<VisualPort> ports) {
        Objects.requireNonNull(
            ports,
            "Visual ports must not be null"
        );

        if (ports.isEmpty()) {
            return Collections.emptyList();
        }

        Set<VisualPortId> usedIds = new HashSet<>();
        VisualPort firstPort = Objects.requireNonNull(
            ports.getFirst(),
            "Visual port must not be null"
        );
        MachineSide expectedSide = firstPort.side();

        for (VisualPort port : ports) {
            Objects.requireNonNull(
                port,
                "Visual port must not be null"
            );
            if (port.side() != expectedSide) {
                throw new IllegalArgumentException("All visual ports must belong to the same machine side");
            }
            if (!usedIds.add(port.id())) {
                throw new IllegalArgumentException("Duplicate visual port id: " + port.id()
                    .value());
            }
        }

        int count = ports.size();
        if (count == 1) {
            return List.of(new PortPlacement(
                firstPort,
                CENTER.offsetU(),
                CENTER.offsetV(),
                CENTER.scale()
            ));
        }

        if (count == 2) {
            List<PortPlacement> placements = new ArrayList<>();
            List<VisualPort> sortedPorts = new ArrayList<>(ports);
            sortedPorts.sort(Comparator.comparing(VisualPort::type)
                .thenComparing(port -> port.id()
                    .value()));

            for (int i = 0; i < sortedPorts.size(); i++) {
                VisualPort port = sortedPorts.get(i);
                PortLayoutPreset preset = i == 0 ? UPPER : LOWER;

                placements.add(new PortPlacement(
                    port,
                    preset.offsetU(),
                    preset.offsetV(),
                    preset.scale()
                ));
            }

            return List.copyOf(placements);
        }

        throw new UnsupportedOperationException("Multiple port layout is not implemented yet");
    }

    public List<PortLayoutPreset> supportedPresets() {
        return List.of(
            CENTER,
            UPPER,
            LOWER
        );
    }
}
