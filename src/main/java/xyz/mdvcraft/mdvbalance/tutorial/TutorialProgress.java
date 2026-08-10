package xyz.mdvcraft.mdvbalance.tutorial;

import java.util.UUID;

public record TutorialProgress(
        UUID uuid,
        String playerName,
        int step,
        boolean completed,
        long startedAt,
        long objectiveStartedAt,
        long updatedAt
) {
    public TutorialStep currentStep() {
        return completed ? null : TutorialStep.fromNumber(step);
    }

    public TutorialProgress advance(String currentName, long now) {
        if (step >= TutorialStep.TOTAL) {
            return new TutorialProgress(uuid, currentName, TutorialStep.TOTAL + 1, true,
                    startedAt, objectiveStartedAt, now);
        }
        return new TutorialProgress(uuid, currentName, step + 1, false,
                startedAt, now, now);
    }

    public TutorialProgress complete(String currentName, long now) {
        return new TutorialProgress(uuid, currentName, TutorialStep.TOTAL + 1, true,
                startedAt, objectiveStartedAt, now);
    }
}
