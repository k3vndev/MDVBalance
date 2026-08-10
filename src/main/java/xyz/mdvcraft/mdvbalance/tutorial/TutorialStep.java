package xyz.mdvcraft.mdvbalance.tutorial;

public enum TutorialStep {
    RACE(1, "race"),
    SUPPLIES(2, "supplies"),
    SURVIVAL(3, "survival"),
    HOME(4, "home");

    public static final int TOTAL = 4;

    private final int number;
    private final String configKey;

    TutorialStep(int number, String configKey) {
        this.number = number;
        this.configKey = configKey;
    }

    public int number() {
        return number;
    }

    public String configKey() {
        return configKey;
    }

    public static TutorialStep fromNumber(int number) {
        for (TutorialStep step : values()) {
            if (step.number == number) return step;
        }
        return null;
    }
}
