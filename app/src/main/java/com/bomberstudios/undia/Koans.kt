package com.bomberstudios.undia

import java.time.LocalDate
import java.util.Random

/** Shown on past days where nothing was drawn. Paraphrased from traditional Zen koans. */
object Koans {
    private val all = listOf(
        "Two hands clap and there is a sound. What is the sound of one hand?",
        "A monk asked: does a dog have Buddha-nature? Joshu answered: Mu.",
        "Before your parents were born, what was your original face?",
        "Have you eaten your rice? Then wash your bowl.",
        "What is the Buddha? Three pounds of flax.",
        "A flag flaps in the wind. One monk says the flag moves, another says the wind moves. The master says: it is your mind that moves.",
        "A cup that is already full can hold no more tea.",
        "The finger pointing at the moon is not the moon.",
        "At the top of a hundred-foot pole, how do you take one more step?",
        "Sitting quietly, doing nothing, spring comes, and the grass grows by itself.",
        "Before enlightenment, chop wood and carry water. After enlightenment, chop wood and carry water.",
        "What is the sound of snow falling on an empty page?",
        "Nothing was written on this day. Is the page empty, or complete?",
        "If you cannot find the truth right where you are, where else do you expect to find it?",
        "The obstacle is the path.",
        "When you walk, just walk. When you sit, just sit. Above all, don't wobble.",
        "Does the day you did not draw on still belong to you?",
        "Muddy water, let stand, becomes clear.",
    )

    /** The same date always gets the same koan. */
    fun forDay(day: LocalDate): String = all[Random(day.toEpochDay()).nextInt(all.size)]
}
