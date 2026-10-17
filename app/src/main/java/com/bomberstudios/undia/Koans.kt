package com.bomberstudios.undia

import java.time.LocalDate
import kotlin.random.Random

/**
 * Shown on past days where nothing was drawn. Some are paraphrased from traditional Zen koans,
 * the rest are written in their spirit.
 *
 * Days are mapped onto a fixed shuffle of the list, so the same date always shows the same koan
 * and no koan comes back until all of them have been shown: with N koans, any N days in a row
 * are all different. Keep N above 366 so that a whole (leap) year never repeats.
 */
object Koans {
    private val all = """
Two hands clap and there is a sound. What is the sound of one hand?
A monk asked: does a dog have Buddha-nature? Joshu answered: Mu.
Before your parents were born, what was your original face?
Have you eaten your rice? Then wash your bowl.
What is the Buddha? Three pounds of flax.
A flag flaps in the wind. One monk says the flag moves, another says the wind moves. The master says: it is your mind that moves.
A cup that is already full can hold no more tea.
The finger pointing at the moon is not the moon.
At the top of a hundred-foot pole, how do you take one more step?
Sitting quietly, doing nothing, spring comes, and the grass grows by itself.
Before enlightenment, chop wood and carry water. After enlightenment, chop wood and carry water.
What is the sound of snow falling on an empty page?
Nothing was written on this day. Is the page empty, or complete?
If you cannot find the truth right where you are, where else do you expect to find it?
The obstacle is the path.
When you walk, just walk. When you sit, just sit. Above all, don't wobble.
Does the day you did not draw on still belong to you?
Muddy water, let stand, becomes clear.
Every day is a good day.
A visitor asked: have you been here before? Yes, said Joshu, have some tea. Another asked the same. No, said Joshu, have some tea.
Ordinary mind is the way.
Do those who are enlightened still fall under cause and effect?
A day without work is a day without food.
Open your mouth and it is already wrong. Move your mind and you have already missed it.
The Way is not difficult, if you only stop picking and choosing.
Without thinking of good or of evil, what is your original face?
A teacher raised one finger. A student raised one finger. The teacher cut it off. What was raised?
Where do you go to escape the cold and the heat? Go where there is neither cold nor heat.
Fetching water and carrying firewood: this is the miraculous power.
Not mind, not Buddha, not a thing. What is it?
If you have a staff, I give you one. If you have no staff, I take it away from you.
A monk asked: what is the way? The master said: it is right before your eyes. Then why do I not see it? Because you are thinking of yourself.
The great way has no gate, and a thousand roads enter it.
Is the moon in the water the same as the moon in the sky?
Show me the mind that you want to put to rest.
The master said: who binds you? Nobody, said the student. Then why seek liberation?
The pine is straight and the bamboo is bent. Which is the way?
A student asked how to enter the temple. The master asked: have you finished your porridge?
What is it that sees, when your eyes are closed?
Bring me the sound of the rain, and I will show you where it hides.
Where is the stillness in a moving river?
The mirror has no mind, and still every face appears in it.
When the pupil is ready, the teacher disappears. Where did the teacher go?
Drink your tea and watch the steam. Which one is leaving?
A student said: I have nothing. Throw it away, said the master.
Who is the one who is hearing this?
Nobody sees the wind. Everybody sees the leaves.
The master was asked about the truth. He picked up a broom and swept the yard.
Can you hold a handful of water by closing your fist?
If the road is steep, does the mountain know it?
What was your face at the moment before this thought?
A student carried a heavy bowl for years. The master said: you may put it down. What happened to the weight?
The mountain doesn't move. The clouds are always arriving.
The bell is struck. Where does the sound come from, the bell or the hammer?
How long is a moment that has no beginning?
A student asked: where does the candle's flame go when it is blown out? The master blew out the candle.
Is the door open, or is the wall missing?
You cannot step into the same river, and yet you wash your hands in it every day.
Tell me the color of the wind.
The cat sits in the sun, and no one has told it what the sun is for.
Who sweeps up the shadows?
A fish does not ask about water. Why do you ask about the self?
The student asked: what is the secret? The master said: there is no secret, so keep it well.
Someone asked how far it is to enlightenment. The master said: you have already walked past it twice.
When the last leaf falls, what remains of the tree?
Where was the road before the first traveler?
How wide is the space between two thoughts?
What do you hear when the clock stops ticking?
A beggar and a king drank from the same stream. Whose thirst was greater?
The wave asks the ocean: when will I become water? What does the ocean answer?
Look for the moon, and you find the reflection. Look for the reflection, and you find the moon.
When the master died, the student wept. What did the master leave behind?
What remains when you have nothing left to explain?
Do not look for the thief. Look for the one who is missing.
The teacher held a flower and said nothing. One student smiled. What was the smile?
A student asked for a lesson. The master handed him a broom.
Why does the sun not ask permission to rise?
Learn to listen to the sound of one page turning.
The empty room holds more than the full one. How is that so?
If you meet yourself on the road, whom do you bow to?
What is the weight of a feather that has not yet landed?
A sword is not sharp in the sheath. Where is the edge?
The master asked: what is the difference between a question and a door?
When the water is still, the moon is perfect. When the water moves, where is the moon?
The student said: I want to be free. The master said: from what, exactly?
In the temple there are many doors, and the wind uses none of them.
Hold the thought lightly, like an egg, or a bird, or nothing.
You walked a thousand miles to ask a question you could have asked at home.
How many days does it take to arrive at today?
Today will not come again. Who is the one who is writing that down?
Yesterday has already finished its work. What is today waiting for?
Tomorrow is a rumor. Today is a page.
Is the beginning of the day at midnight, or at the first breath?
A day is a small life. How do you spend it?
The page is blank only until you arrive.
If today is only drawn once, what will you draw?
What is a day made of, if you take away the hours?
One day contains all days. Find the one that is missing.
The first hour has no memory of the last. Which of them is today?
The calendar says one thing, the sun says another. Which one is the day?
The page ends at midnight. Does the day?
What does a day weigh, when it has been filled?
If a day goes by unwritten, who has lost it?
Open a page you cannot edit. What do you learn?
A page filled today cannot be filled tomorrow. Where does the rest of you go?
The old days are not closed. They are only finished. What is the difference?
Count the days you have not counted.
What has been written is written, and what has not been written is still free.
Do not hurry. The day is exactly as long as it needs to be.
Seven days make a week. How many make a moment?
The year is long, but the day is only one page.
Waking up is the first thing you do every day. Do you do it well?
Is a page that no one will read still a page?
A day with no words is a day with all words. Read it.
What is the length of a day that never started?
You cannot take back a line. Can you take back a day?
Draw a line. Now leave it alone. That is harder than it sounds.
Ink does not apologize.
The pen touched the paper, and the paper became a witness.
What is the thinnest line you can draw that is still a line?
If you draw a circle and step inside, where is the outside?
One stroke can be a mountain, or a river, or the back of a chair. Which did you intend?
A line goes from here to there. Is it the line that moves, or you?
The stroke is gone, but the hand is still moving. Whose stroke is it?
The ink dried and the story was fixed. Who is still writing?
Mark the page, and the page has a history.
You cannot erase what you have not yet drawn. Begin there.
A mistake on a page without an eraser is simply a line.
If a line is crooked and you love it, is it crooked?
Write the thing you would not say aloud. Who reads it?
The pen has no opinion. Where does yours come from?
One dot is the beginning of every drawing. Where did you put yours?
The blank page is not afraid of you. Why are you afraid of it?
You drew the line without thinking. Who drew it?
Draw the sound of a bell.
What does the pen know that the hand has forgotten?
When there is only one page, there is only one chance. Is that sad, or generous?
Put the pen down. Is the drawing finished, or are you?
A line is a path someone walked once. Walk it.
The ink is darker than the thought. Which one is true?
If a stroke fades before anyone sees it, did it happen?
Draw the day, and then say what you left out.
The thin line holds the whole picture together. Where is its strength?
What is the opposite of a line?
The pen lifts. The line stays. Where are you?
To write a word is to let it go. To let it go is to write it.
Cross out what you wrote. Now it is both there and not there.
Whose handwriting is this, if not yours?
Say it with one line.
What does a page look like to a pen?
The brush does not know what the artist knows. The artist does not know what the brush knows.
Two lines cross. Which of them owns the crossing?
Spring comes, and the grass grows by itself.
The old pond. A frog jumps in. The sound of water.
A single leaf falls, and the whole autumn is in it.
The snow covers the field, and yet the field is there.
The mountain is not tall until someone looks up.
The stream does not argue with the stones.
Under the bridge, the water has never heard of the bridge.
The crow on a branch, the moon behind it. Which is the stranger?
How does a seed know when to open?
The river runs to the sea, and has never left the river.
The sky has no corner to put your thought in.
The moon does not leave the sky to visit the lake.
After the rain, the sky has forgotten the rain. The puddle has not.
Hear the wind, and the wind is gone. Hear it again.
The stone in the garden has no name. Is it lonely?
What does the tree do in winter?
Morning glories open for a single day, and they are not sorry.
The bamboo bends under the snow, and is still bamboo.
The moon was never lost, only covered.
Where does the shadow go at noon?
In the summer, the cicada sings. In the winter, it is silent. Which is the cicada?
The fog lifts, and the mountain was always there.
The echo asked the valley: who is calling?
A single drop of rain falls on the lake. The lake does not mind.
The deepest part of the pond is the part you cannot see.
The path is made by walking, and unmade by the grass.
The sparrow is not troubled by the size of the sky.
How loud is the silence after the last note?
The sun rises on the just and the unjust. Who gets the shade?
A crane stands on one leg in the shallows. What is it waiting for?
In the garden, the weeds and the roses have equal rain.
The cloud has no plans, and still it arrives.
Water takes the shape of the cup, and the cup takes the shape of the thirst.
Tea steams. Rain falls. Nobody asked either of them to.
The pebble on the path was once part of the mountain. Which is more complete?
The grass bends in the wind. Who is the wind?
A cold morning. The kettle is already singing.
The temple bell rings. The mountain answers.
The lantern is lit. Where is the darkness now?
The sound of the oar, and the lake knows nothing about it.
If you sit still long enough, the birds will ask you about yourself.
The one who walks the path and the one who arrives are not the same. Which is you?
When you are hungry, eat. When you are tired, sleep. Why is it so hard?
Boil water. Make tea. Wash the cup. Begin again.
A student asked what Zen is. The master said: wash the dishes.
The teacher said: pay attention. What did the student pay?
The master was asked about the meaning of life. He offered a cup of tea.
You are looking for your glasses. They are on your face. Where are you?
If you are not here, where are you?
You carry your own mountain. How heavy is the one you have not noticed?
You are the one who is looking for the one who is looking.
Take one step and you are already further than yesterday.
Your hands know the way. Why does your mind ask for directions?
Everything you need is in the next breath. Take it.
You have been walking with a stone in your shoe and a question in your heart. Which will you remove?
The moment you name the pain, whose is it?
Who is it that wants to be happy?
You can only take what you carry. What are you carrying?
You reached for the star and caught a firefly. What did you do with it?
When the cup is empty, it is also the right size.
The one who hurries to the end arrives at nothing.
Don't push the river. Notice who is pushing.
There is nothing to be found, so stop searching for a while.
How do you say hello to yourself?
The heart is a house with no walls. Who lives there?
Anger is a hot coal. Who is holding it?
You are not behind. Behind what?
A man asked a question and the answer arrived too early. What happened to the question?
If you could change one thing, what would be left to change?
Walk slowly and you pass nothing. Walk quickly and you pass everything.
When you stop searching for the exit, notice what the room looks like.
Make a wish. Now notice who made it.
How do you thank the stone that made you stumble?
Your shadow follows you at every step and has never asked for anything.
The path ends here. Which of you knew that?
Today is the only day you will be the age you are now.
What would you do if no one would ever see it?
The deepest bow is the one nobody notices.
You are loved by the sound of your own name. Who is it that hears?
The tea is hot. The cup is full. The moment is exactly the size of your hand.
If you had no name, what would you answer to?
Sorrow and joy are two rooms in the same house.
Do you listen to answer, or listen to hear?
When you give a gift and expect nothing, what did you give?
If you are lost, you are already somewhere.
A door that opens only from the inside: who made it?
What does the knot know about the rope?
The key is in the lock. The door is not locked. Who put the key there?
The gate is open. Why do you wait for the gatekeeper?
A student asked: what do I need? The master said: less.
To own nothing is to be weightless. To want nothing is to be free.
A person who has everything is looking for a smaller house.
When the net is empty, the fisherman has the whole sea.
The one who dares to be alone is never lonely.
The moon is the same to the thief and to the saint.
He who knows he has enough is rich.
A wise person is a fool who has stopped pretending.
The beginner's mind has many possibilities. The expert's mind has few. Whose mind is yours?
Say nothing, and be understood.
To know a thing is to lose it. To lose a thing is to know it.
The road to the temple is also the road away from it.
The teacher said: I have nothing to teach you. The student said: thank you, I have learned it.
A bird sings not because it has an answer, but because it has a song.
A silent mind makes room for a whole mountain.
When you point, I look at your finger. When you speak, I listen to the air.
The empty bowl was left on the doorstep. By morning it was full of rain.
Can you hear what is not being said?
The teacher is the question that will not leave.
A mind like a clear sky does not need a cloud to be loved.
If you are always arriving, when do you rest?
Peace is the space between the questions.
The arrow has left the bow. Who is aiming now?
A leaf on the water goes where the water goes, and arrives anyway.
Even the master was once a beginner who did not know what a beginner was.
The monk asked: what is the highest teaching? The master said: close the window, it is cold.
A student left for a pilgrimage. He came back and found the temple in his own house.
I cannot teach you to swim on land.
When the stream is crossed, do not carry the boat.
If a word could contain the truth, the truth would be small.
The student asked: how do I become empty? The master said: how do you stop being full?
What is the sound of a page that has been turned a hundred times?
A student asked: why is the master so happy? The master said: I have stopped asking for more.
Where does a thought live when you are not thinking it?
The monk asked: how can I avoid suffering? The master said: who is suffering?
The mind is a monkey. The monkey is a mind. Who is the zookeeper?
A thought arrives. It passes. The sky does not call it a visitor.
What did the clock say when time stopped?
Time does not pass. You do.
Is it time that passes, or the page that fills?
Between one breath and the next, who is breathing?
A minute is long if you watch it. A day is short if you live it.
If a bell rings in an empty house, is it still the same hour?
The clock asks nothing of the sun.
Time is a river with no banks. Where do you stand?
The present is a very small place. Everyone lives there.
Before the clock was invented, was it late?
Memory is a page you cannot see until you turn it.
A year is a day that remembers itself.
Eternity is not long. It is wide.
What does the moment ask of you?
The past is a door that closes softly. Which of them is open?
If you stop counting, how long is a day?
Even the oldest page was once new, and the newest page is already old.
The future is a blank page, and the present is the pen. Who holds the paper?
What is the shortest time you have ever lived?
The hour hand and the minute hand meet only twice, but they have never fought.
Let go of yesterday's page. It was never yours to keep.
When the day ends, who turns the page?
A hundred years from now, who will mind that this was crooked?
The ink does not know it will fade. Do you?
Everything fades. Is that a loss, or the shape of a gift?
Nothing is permanent, and yet the mountain looks permanent. Who is mistaken?
When a thing is gone, is it more or less itself?
The candle burns because it is going out.
The snowflake falls only once. It does not ask for a second turn.
The flower and the weed are both gone by winter. Which was more grateful?
Is a drawing that fades less a drawing?
A page that is only for you is the best audience you will ever have.
Be like the water: it fills every shape and keeps none.
The tide goes out, and the beach is clean. Who drew on it?
Ashes fly up, and where is the fire?
A footprint in sand is a short sentence. Is it less true?
The fire has gone out, and the warmth is still in the room. Where is it?
Loss is the shape of what was held. Hold something today.
You cannot keep the sound of a bell. You can keep the silence it leaves.
All of this will be gone. Isn't that why it is so bright?
Do you remember what you did last Tuesday? Who does?
A small thing done fully is not small.
The page asked the pen: what will you leave? The pen said: only what I'm given.
The sun does not hurry, and still every morning comes.
Nothing is hidden. It is only that you were looking elsewhere.
The ordinary is the only miracle. Look at it.
How do you count a river?
Have you looked at your own hand today?
The ladder has no top. What is the next rung for?
Do not seek the truth. Just stop having opinions.
A frog in a well knows the sky. How large is it?
If you cannot say it in one line, say it with silence.
Everything is complete. What is missing?
Hold nothing. Be held by it.
Is the teacher in the book, or in the one who reads it?
A student learned a hundred names for water and was still thirsty.
Be as lost as a cloud, and arrive everywhere.
A white page contains every black one.
The questions fall from the tree. Who is picking them up?
The wise one is not the one who has answers, but the one who is comfortable without them.
When you bow, who is bowing and who is receiving it?
A master and a student sat together in silence. The next day, they met again and continued the conversation.
How can you tell a fool from a sage? One cannot say.
A rock on the path is an obstacle to the hurried and a seat to the tired.
It is not the destination that is missing. It is the traveler.
The teacher was asked: what is the greatest wisdom? He said: when it rains, take an umbrella.
You asked a question. The silence is the answer. Now ask the silence.
I have no teaching. Come and have some tea.
You have always been at home. The road was a long way of arriving.
The student said: I do not understand. The master said: good, now we can begin.
If you want to see the wind, look at the grass.
Do you see the page, or the white around it?
Do not seek the footsteps of the ancients. Seek what they sought.
Be careful where you step. The ground is full of old stories.
The student's mind was full of knowledge. The master poured tea until it overflowed. The student said: stop. The master said: so it is with you.
The stone sinks, and the ripples reach every shore. Which is the act?
Once there was nothing, and then there was a pen. What is next?
Pause. Breathe. Draw.
Quiet down until you can hear the page.
The sound of paper is the sound of a day being kept.
Hold the pen as if it were a bird: not so tight that it cannot breathe, not so loose that it flies away.
The page remembers nothing but the mark. Why do you remember so much more?
Write the sentence you would give to a stranger who has one minute.
A drawing is a conversation with a person you will not be tomorrow.
What would you draw if the page would never be seen again, and what if it would?
The one thing you are afraid of drawing is the one thing worth drawing.
Draw a line for the day you almost forgot.
Make a mark that is not in any way a message.
Today's line is today's. Tomorrow's will be tomorrow's. Where is the line between them?
Look at yesterday's page, then look at your hands. Who made it?
How does the page know when you are done?
You may look back, but you may not touch. What is that like?
The finished page and the unfinished page are equally finished.
The pen is lighter than the thought, and the thought is lighter than the day.
Tomorrow's page is already on its way, and it will be white.
Write what you saw, not what you know.
A page that was never written on is perhaps the one that was most listened to.
What does a blank day look like when you read it a year later?
To keep a day is to leave it alone.
A line you cannot erase teaches you the shape of care.
Care for the line, and the picture will care for itself.
Fill one page, and you have filled the world, but only in one corner.
When the day is over, put down the pen and say thank you to nobody.
Look at today's page. It is the only one that does not yet know how it ends.
You drew something, then let it go. What have you taught the page?
The pen has nothing to prove. Draw anyway.
A hundred lines made a wall. One line made a door.
If the line wavers, the hand is alive.
A blank page is the only honest teacher.
Not every line needs a reason. Draw one anyway.
""".trimIndent().lines().filter { it.isNotBlank() }

    // A fixed shuffle, so neighbouring days are unrelated; the seed must never change
    private val order = all.indices.shuffled(Random(1))

    fun forDay(day: LocalDate): String =
        all[order[Math.floorMod(day.toEpochDay(), all.size.toLong()).toInt()]]
}
