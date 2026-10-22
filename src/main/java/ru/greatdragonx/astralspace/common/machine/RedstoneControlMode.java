package ru.greatdragonx.astralspace.common.machine;

public enum RedstoneControlMode {
    IGNORE,
    WHEN_POWERED,
    WHEN_UNPOWERED;

    public boolean allowsOperation(boolean hasSignal) {
        return switch (this) {
            case IGNORE -> true;
            case WHEN_POWERED -> hasSignal;
            case WHEN_UNPOWERED -> !hasSignal;
        };
    }
}
