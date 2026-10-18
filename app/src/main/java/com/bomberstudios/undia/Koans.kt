package com.bomberstudios.undia

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shown on past days where nothing was drawn. About a third are funny (wry, deadpan, a bit
 * surreal) and the rest are quiet, so that a funny one comes as a surprise. A koan may contain
 * {day}, which becomes the name of the weekday it is shown on.
 *
 * The list is the schedule: the koans are in the order they are shown, one a day, and the funny
 * ones are spread through it (never more than two in a row, never more than four quiet ones
 * without a funny one). With N koans, any N days in a row show N different koans, so keep N above
 * 366 for a whole (leap) year without a repeat. [OFFSET] decides which koan falls on which date:
 * changing it, or the list, moves every koan to a different day.
 */
object Koans {
    private const val OFFSET = 89L

    internal val all = """
The master’s only teaching was: wear the good socks. Nobody understood until winter.
What do you carry that is lighter than a feather and heavier than a mountain?
To be carried by a river you must first stop swimming.
What is the sound of a very old door being opened for the first time?
Does the stapler ever dream of freedom? Probably not. It dreams of staples, and the staples are, for now, enough.
A very small bell hung in the doorway of the temple. It rang whenever a thought came in. It never stopped ringing, and nobody had the heart.
In the empty room, someone is sitting. It is you, and you are not alone, and you are not two.
Is it still a puddle if it’s very deep? Ask the person whose foot is in it.
Hold out your hand. Notice what it is already holding.
What is the ground, when you stop standing on it?
A lamp asked the dark: do you mind? The dark said: do you?
Tonight the stars will be exactly where they were last night, only slightly less sure of themselves.
The teacup trembled with ambition. The tea said: don’t.
Even the shortest day contains the whole sun.
There was a bird on a wire, and the wire was on a bird, and that was the whole story, and it was enough.
What spoon? What soup?
A stone is thrown into a pond. Where is the stone, and where is the throw?
The water takes the shape of the bowl. Whose shape does the bowl take?
A cat sat on the drawing. Now it is a collaboration.
A river does not follow a map. It makes a map, and then ignores it.
The page is waiting. The page is always waiting. It is the world’s most punctual thing.
Two dots and a curve is a face. One dot and a curve is a very suspicious smile.
Sit by a window and let the day come to you. It will, but slowly, and on its own terms.
Sweep the floor. Sweep it again. Is the floor getting cleaner, or are you?
When a person says “I’ll be there in five minutes” the universe takes a sharp breath.
The page is white. The snow is white. The difference is who is going to walk on it.
We keep very quiet to hear the small sound of the day turning over, like a sleeper, or a page.
Knock, knock. Who’s there? Nobody. Nobody who? Exactly.
What was your face the moment before you were asked who you are?
A broom has no idea what a mess is. It simply believes in the other way.
A potato has eyes but no opinions. This, the master said, is the whole of wisdom.
A thousand lamps are lit from one flame. What did the first lamp lose?
A shadow does not leave a room. It is only the room being honest about the lamp.
A very old cheese was given the job of watching the house. It did, all night, in total silence. In the morning: nothing was missing.
Somewhere a very patient hand is waiting to turn a page.
One snowflake falls on a snowfield. The field does not notice. Neither does the snowflake.
A fish does not know what a bicycle is. A bicycle does not know what a fish is. They get along fine.
Somewhere a person is learning the name of a bird. The bird does not know it has a name.
Plan the day. The day will get a good laugh out of it.
The horizon is just a very polite form of “not yet”.
The first light is not in the east. It is in the one who is awake to see it.
A shadow was asked how it felt to follow someone all day. “It’s a living,” it said.
An old well is not empty. It is full of echoes.
Perhaps a day is a very small jar, in which you keep a thing you won’t remember.
The question mark is a hook. Go fishing.
A page is not a rule. It is a field, and the crows are already in.
A haiku walks into a bar. It’s short. It’s got a lot to say. Nobody’s listening. It’s fine.
At dusk the sparrows go quiet one by one. Which was the last sound?
How long does a moment last when no one is looking?
The wave and the sea: where one ends, the other has not begun.
There is a fly on the page. It has no opinion on the page. It has strong opinions about the ceiling.
You can hold a stone, but can you hold its weight?
A mother calls a child in from the garden. The garden does not mind being left.
There are three kinds of people: those who understand the kettle, and those who do not.
The window is clean. Where did the glass go?
At the edge of the field there is a fence. On the other side there is also a field.
How many days fit in a week? Ask a week.
Sit until the dust in the sunbeam is the only thing moving. Then sit until it stops.
A single thread of smoke rises straight up and is gone. Which is more honest, the smoke or the fire?
An ant carried a crumb ten times its own weight and was asked how it felt. “Large,” it said.
Where does the fire go when it goes out?
If a day passes and no one notices, does it matter? It mattered to the day.
The student asked the master how to know if he was dreaming. The master pinched him. “Ow.” “Probably not, then. Sorry.”
It is raining on a field nobody owns. Who is it for?
The student asked: what is the sound of one hand typing?
How much of what you carry is yours, and how much is the road’s?
A bell is struck, and the whole valley answers. Who was calling?
How long does it take a lemon to become a metaphor?
The moon rises. Somebody is looking at it from another country, and they are both looking at the same thing, and they are not in the same place.
You do not have to carry the whole day. Just the part that is in your hands.
There are days when the only thing to do is notice the light on the floor.
The master has two kinds of days: the ones he’s proud of, and the ones where he ate the whole pie.
The cherry blossoms are falling. A man asks them to stay. They say: we are not leaving. We are arriving elsewhere.
On the shore a child builds a castle. The tide is patient. The child is patient. Both will be right.
All the doors in the temple were labelled “push”. The master pulled. That was the whole lesson.
Imagine a door that has never been opened. Now imagine it is you. Now imagine you are slightly ajar.
Every river tries to get to the sea. Meanwhile, the sea is trying to get to {day}.
Once there was a stone that wanted to be a cloud. It practised on the very still days. Nobody has seen it fly. Nobody has seen it not fly.
Take a breath and notice that it did not ask your permission.
The monk asked: what is the sound of one mitten? The master put it on and clapped. Nothing. “There,” he said.
To forget a face is to have loved it a little less. Or a little more. Nobody has ever been able to say.
Some days you are the hammer. Some days you are the thumb.
The path goes up. The path goes down. The path is not interested in your opinion.
A philosopher asked a cat what it was thinking. The cat said: “The usual.”
The night sky is full of old light. All of it arriving very late, and all of it on time.
A road that disappears over a hill is not gone. It is only in the next part of its journey.
The sea has never been on time and nobody has ever been angry about it.
A beginner takes the stairs. A master takes the lift. A sage waits for the other person to press the button.
A day on which nothing was drawn is not a lost day. It is a day that saved its ink for later.
What happens to the yawns we don’t finish?
A cloud has a very short career, but it does a lot of research.
Whose footsteps are these, which walk just behind you in an empty street?
Every cat is a small, furry ambassador of the afternoon.
The sky changed its mind and rained. Everyone agreed it was a good change of mind.
The oldest stone in the garden is not the largest. It is the one that is already part of the path.
Between one thought and the next, there is a very small, very comfortable sofa.
The teapot said: I contain multitudes. The cup said: I contain exactly one.
A quiet room has more in it than a loud one. Just ask the dust.
A thread is pulled, and a sleeve unravels. Where does it end, the thread or the sleeve?
A walrus walks into the temple and says: I’d like to be enlightened, please. The monks say: we are out of that. Would you like soup?
The oldest river in the world is a long way from any sea, and is not worried.
There is a space between the first sip of tea and the last. It is slightly warmer than the rest of the day.
Whoever put the stars up there did not leave a manual. Or did, and it’s under the couch.
A tiny knot in a ribbon is the beginning of a very long story. Ask the cat.
The ink dried. Whose idea was that?
When the master was asked about the nature of time, he checked his wrist. He wasn’t wearing a watch. He said: well, there you go.
When the master finished his tea, he said: and now, the washing up. The student said: is that a metaphor? The master said: it’s a pan.
What is the weight of a shadow at noon?
The spider in the corner has been building the same web for a week. Whose idea was it?
Something very small happened today, and it was exactly the right size.
Only a very small part of the sky fits in the window. It is enough.
A very long journey starts with a very small snack.
A clock has no idea what time it is. It only knows how to move.
A student asked how to stop worrying. The master said: five minutes, then lunch.
Somewhere a very old lantern is still alight, though nobody remembers who lit it.
To be silent is not to be without sound. It is to listen to the sounds that were there all along.
A stitch in time saves nine. A stitch in the wrong time doesn’t get a vote.
The question has been answered. The answer has been forgotten. What is left is the quiet shape of having asked.
What is the sound of a pen hovering?
A mouse in the temple was given a name by the monks. It has no use for it. It answers to cheese.
To see the moon, do not point. Wait for the clouds.
The night is not dark. It is only the day being quiet.
You can’t step in the same river twice, but you can lose the same sock in it.
A very old man sat on a very old bench and said, to nobody: well. And the bench said nothing, and agreed.
Today you have exactly as many hours as a king, and slightly fewer pockets.
Open the window and listen. Not for anything. Just open it.
A mountain is just a very long afternoon that decided to stay.
Everyone is looking for the answer. The question is hiding behind the couch with the sock.
The shadow of a leaf on the wall is moving. The wall does not mind. The wall is, in a way, listening.
The crane stands on one leg. Which leg is the crane?
The wise one said: do not trust anyone who says they have no snacks.
In the tide pool there is a small, perfect world with no idea it is small.
Some days the page is a field of snow. Be careful where you step. Or do not.
In the empty field, a single scarecrow. It has seen everything and is not saying.
A monk who had never seen a mirror asked the master who lived in the pond. “Ah,” said the master. “That’s the one who never talks back.”
There is a cloud shaped like your next idea. It is moving quickly. Be at the window.
The master was asked about the sound of one hand clapping. He said it sounds like a high five left on read.
Hold a shell to your ear. It is not the sea. It is the shape of the listening.
The man asked the river: what are you thinking about? The river said: leaving. Always leaving. But also: lovely rocks.
A student asked what happens after death. The master asked what happens before breakfast.
Look at your hands. Whose are they, in the end?
Let the dust settle. It will, eventually, with its feet up.
Here is a very good dot. Look after it.
What did the pencil say to the page? “I only have one thing to say, and I’m not sure it will fit.”
The most enlightened person in the village was a very old man who had no idea he was enlightened. He thought he was just tired.
After the last guest leaves, the house is filled with all the things they did not say.
There is nothing more to add. The page is already full of what it did not say.
The kettle is boiling. The student is waiting. The master is not worried. The kettle is not worried either. The student is a little worried.
The cup is full of tea. The tea is full of cup.
A bridge is a very patient staircase.
A man sat by the lake until the lake forgot him. Then he was truly there.
The monk tore the page in two. “What is this?” “Two pages.” “Ah, so, progress.”
Between you and the page there is a very polite silence. Do not break it. Draw it.
A mushroom wore a leaf like a hat. Nobody told the mushroom. What hat? What mushroom?
Nothing happens on a {day}, and it’s a very good nothing.
When the last bird leaves, is the sky larger, or only emptier?
The pen said: let me tell you a story. The page said: I’m all ears. (The page has no ears. This was a figure of speech.)
Something is moving in the grass. It is a very small something. It has somewhere to be.
A fork in the road, at last. The master took the spoon.
The more you read the sky, the stranger the grammar.
Look at the tree. Now look at the space between the branches. Which one is the tree?
A cup is a hole with ambitions.
Take a breath, and have a look at this breath: is it a good one? It’s yours, so, yes.
What you are looking for is looking for something else.
A monk asked how to reach enlightenment. The master said: first, find the stairs. Then ask why there are stairs.
The goldfish knows nothing of the shore. The shore knows nothing of the goldfish. Nobody is the poorer.
How many pages must one turn to become a book?
What {day}? What afternoon?
A raindrop on the window was asked how long it had been waiting. “Since the cloud,” it said. “I’m in no rush.”
The cloud drifts. The mountain stays. Between them: a very unclear friendship.
The mountain does not know it is a mountain. It has never been asked.
A moth drifted towards the lamp with the calm of a person who has read the manual and decided against it.
A pencil does not know it will be a sentence. It is just trying its best to be a line.
The lamplighter walks the empty street. Somebody, somewhere, is looking out of a window.
The master asked the cabbage what it thought of the weather. The cabbage had a leaf on the matter.
The door asked the handle: are you coming? The handle said: I’m already in your hand, you daft door.
A line is just a dot that went for a walk.
Before anything was written, whose was the silence?
Leave a little room on the page, as you would for a guest.
The student asked: how do I become empty? The master said: leave the fridge door open.
A circle is a line that finally made it home.
Your name is something other people use. What do you call yourself when nobody is near?
Nobody has ever seen the wind. It is, at best, a rumour with great hair.
Do not look for the end of the path. The path is not looking for it either.
A footstep fades. The ground remembers a little, then forgets, then is the ground again.
What pen? What page? What exactly was the hurry?
Today is a very small blank door. Please knock with a pen.
A bell rang in the empty temple. The mice looked up, and decided it was probably for them.
The sunset does not ask if you are watching.
A cup of tea at dusk, a book left open, a sleeping dog. That is the whole of it.
Does a duck know it is on the water, or does the water know it has a duck?
The student asked how to meditate. The master said: sit down, be quiet, look at any thing. A door is fine.
What do you hear before you hear anything?
What do you hear when you hear nothing? Listen again.
A snail does not worry about a deadline. Neither does a deadline worry about a snail. It’s mutual.
No bird has ever had a bad day at the sky.
If a page is never read, is it still full? Is it still a page? Is it still {day}?
What remains of a day when the day has gone? A very faint warmth, like a chair.
A cloud is a daydream the sky is not ashamed of.
The ink is wet. So is the cat. Nobody’s very happy about either.
The stairs go up and the stairs go down, and you are standing on the same step.
Even the mountain has a bad knee, somewhere, in the long grass.
After rain, the garden is a different kind of quiet.
Every journey of a thousand miles begins with a single lost shoe.
It is possible to be alone in a crowd, and not alone in an empty room. The room knows.
In the end, all the tea will have been drunk. It’s the middle part that counts.
A turnip became enlightened. Nobody noticed, including the turnip.
The garden is the one place where “later” is a perfectly good answer.
Is it a coincidence that every chair has four legs and every day has one too many?
Listen to the very small sound the evening makes just before the lamp is lit.
You cannot hold a day. Yet it holds you all the way through.
The master said: all things must pass. A bus passed. The master missed it, and said that too.
There is a bowl on the table. Sometimes it is empty. Sometimes it is not empty yet.
A letter was written and never sent. It is still travelling.
A good question is a door. A bad question is a door with a “pull” on it that says “push”.
How wide is a {day}?
The master said: do not mistake the finger for the moon. The student said: never once. The master said: that’s what the moon wants.
How far is it from the question to the one who asks?
A pond does not care about the frog. The frog cares about the pond. Both are right.
The road behind you is not the road you walked. It is the road that remains.
Is it still a hat if the wind is wearing it?
How do you hold a handful of water? Ask a very good bucket.
A hundred lines walked into a bar. The bartender said: that’s a lot of lines. One said: we’re a drawing.
A boat is tied to the shore, and the shore, in a way, is tied to the boat.
There is a corner of the page that nobody ever uses. Go and say hello.
One day the master said: today I will do nothing. By evening he was exhausted, and the garden was flawless.
There is a gate with no wall. A person walks around it. Why?
Between the inhale and the exhale is a door. Do not go in. Just notice it is there.
A tiny seed holds the whole forest, and has no interest in the forest, and is not troubled.
A man asked the sea for directions. The sea said: yes.
When you look at a mountain, who is looking?
The elephant in the room asked to be introduced. The room, embarrassed, said it knew nothing about it.
The snow ends where the footprints begin, and begins where they end.
The master swept the same leaf for a week. “It keeps coming back.” “It’s a very persistent leaf.”
Somewhere a very long train is passing through a very small town, and a person is waving at it. This is the whole story.
Even the sun has to get up in the morning, and it does not say a word about it.
There is a very small hole in everything. This is called {day}.
A grain of rice, held up to the light, contains everything but is not impressed.
A monk asked what is the best day. The master said: the one with the sandwich.
The afternoon is the part of the day that is not trying to impress anyone.
What is it that you cannot throw away? And why do you still carry it?
What does an empty page look forward to?
What would a day be if it had no name? Probably a bit relieved.
A leaf fell. The tree said: right on schedule. The leaf said: nobody told me.
A monk lit a lamp for a blind man. “Why?” “So that you will not walk into me,” said the blind man.
A grasshopper asked the master how to jump higher. The master said: first be a very small ladder.
A small thing, done with great attention, is a very large thing, in a very small jacket.
In deep snow, a single crow. Which is the darker?
A tiny dragon lives in the pen cap. It is not the dragon’s fault. It was just warm.
A hand held out in the dark finds another. This is the whole of the teaching, and it is very small.
A day is a small boat. What did you put in it?
A line walked into a page and said: I’ll have what the dot is having.
The morning is not early. It is exactly on time.
Everything you forgot is still somewhere. It is just not where you were looking.
A crow stole a button. The monk said: well, that’s one less thing to be attached to.
A dog waits at the station for a train that stopped coming years ago. Who is the more faithful, the dog or the station?
How tall is the silence after the toaster pops?
Today has not been drawn on yet. This is fine. It is a very patient today.
A candle in an empty room: is it keeping the dark company, or the other way round?
The moon asked the lake for a moment of its time. The lake said: I’m busy being the sky, but fine.
In the evening the mountain turns blue. Is it the mountain, or the evening?
What is the sound of an empty cup being set down on a table?
If you listen to a conch shell you can hear the sea. If you listen to a toaster you can hear the morning.
Once there was a day so ordinary that nobody remembers it. It was perfect.
Cut a tree and count the rings. Which ring were you, in the year of the long drought?
Once a day, for a moment, the cat is a lion. Do not mention it.
It is possible to know a place by the sound it makes when you stop walking.
If a hand reaches out and there is nothing there, what is it holding?
The temple bell has a crack. It still rings. It rings a little differently. That is the sound of this bell.
The sun has been doing this for billions of years and still no one has said thank you. Say it today. It will pretend not to hear.
A sound is made. It is gone. Where did it live?
When the cloud has gone, the moon is not more itself. You are simply seeing it.
You don’t have to understand everything. You have to understand the sandwich.
A drop of ink falls into a glass of water. The water does not say: that is not mine.
A cloud applied for a job as a pillow. The interview went well, but it kept drifting off.
There’s a distant sound, like a small bell, like a very polite thunder. Nobody knows what it is. It’s probably lunch.
The tide went out. The tide came back. Nobody asked where it had been. It’s polite.
Autumn comes. The leaf lets go. Nobody asks the tree.
“Master, what is the meaning of life?” “I’m on my lunch break.”
A day is not long, a year is not long, a life is not long. And yet. A very long afternoon.
The old monk said: there are two ways to do things: my way, and the way that’s a bit more complicated and takes longer.
Today’s sky is the same as yesterday’s sky, only more of it.
What is the opposite of a good idea? A good idea at 3 a.m.
The pen has a secret: it only writes in the present tense.
Draw a bird. Draw the sky it is not in.
To say what a stone is, you would have to be a stone. And then you would have nothing to say.
When the monk reached the end of his rope, he found a second rope. It was knitting.
A student asked how many monks it takes to change a lightbulb. The master said: it is not the bulb that is dim.
The mirror is clear. Who is it that wipes it?
In the garden, a stone, a leaf, a very small bird. It is a complete sentence.
The best drawing is the one nobody asked for.
No one wins at chess against the weather.
Somewhere a clock is wrong in a way that makes it right. What time is it there?
In the moment before the rain, everything in the garden holds its breath.
Time flies like an arrow. Fruit flies like a banana. The monk, confused, ate the banana.
If today were a color, it would be the one just before you decide.
There was a leaf on the road. Everyone stepped around it. Somebody wrote a poem. The leaf was unaware.
What is it like to be the last biscuit in the tin?
A short line has more room to think.
What shape is the quiet that stays in the room after a door closes?
Somewhere a tiny pencil is writing a very long novel on the sole of a boot.
Under the tree there is a bench. Under the bench there is the memory of everyone who sat.
Before there was a day, there was no today. Where did it come from?
Asking the sky for rain is a very long email.
A guest arrives at an empty house and is welcomed by the silence. The silence is a good host.
The light falls on one side of the cup. The other side is also the cup.
If you can’t find the thing you are looking for, it’s probably in the thing you were looking for before.
The wise person keeps a small silence in a pocket, for emergencies.
The lake holds the sky without trying. Try that.
The master lived on a mountain for thirty years and came down only to ask where the good soup was.
A thin line of light under a door. Behind it, somebody is awake.
If a bear sits in the woods and nobody makes a joke about it, is it still a bear?
It takes three seconds to say hello, and a lifetime to wonder if the hello was too loud.
A dandelion asked the wind for a lift. The wind said: sure, but I’m not going far, and I’ll be honest, it’ll be a bit all over the place.
The river is not in a hurry, and still it gets there.
A window, a table, a cup. You have everything the afternoon requires.
The master was asked for a one-word summary of the teaching. He said: “Soup.” Then, thoughtfully: “Also soup.”
What is today, if you take away the hours, the weather and the sandwich?
You can never be lost in the place you are standing.
Who is it that is listening to your thinking?
A monk asked whether a dog has Buddha-nature. The dog, overhearing, asked whether the monk has dog-nature.
In the middle of the afternoon, in a very quiet house, a clock says one thing. It is enough.
The master sat so still his tea went cold. He warmed it with his mind. Then the tea got jealous.
A pebble in a shoe is the world’s smallest teacher.
A dot is a point of view. A line is an argument. A page is a place to have it.
Not every dot is a beginning. Some of them are just a very short pause.
Lightning does not ask permission. Thunder arrives later with the paperwork.
A single raindrop on a spider’s web: a whole world, trembling.
Seeking the way, a monk walked a thousand miles. At the gate the master said: you could have emailed.
When you press your ear to the page, you can hear nothing, and that is the whole point of a page.
A leaf lands on the water and the water makes room.
The master was asked what he meant by “no mind”. He said: exactly that, and also, where are my keys.
The tea leaves settle. The cup becomes clear. Now what?
In an old temple, a very old cat sits in a very old window, and nobody has ever been able to prove it isn’t the abbot.
A river passes under a bridge. Is the bridge standing still, or is the river?
A page is a tiny field. What are you planning to grow?
A pigeon sat on the master’s head for an hour. “Did you mind?” “I thought it was a hat.”
Even a blank page has a small mark: the page.
How many breaths ago were you somewhere else?
Between a beginning and an ending there is a very ordinary {day}. Pay attention to it.
If you shout at the mountain, the mountain shouts back, but later, and in a lower voice, and only if it feels like it.
What is the colour of the first thought of the morning?
What kind of day has a “closed” sign and a very small bell?
An old pine does not hurry. An old pine does not wait.
A rock in the stream asked: why is everything always going past me? The water said: because you’re a rock, Gary.
The master pointed to the sky and said: look. The student looked. A pigeon looked back.
When the clock stops, the day begins. When the day ends, the clock is already asleep.
How does a snowflake decide? It doesn’t. It’s a very trusting design.
Is a day made of hours, or is an hour made of days, in miniature?
A monk and a mouse shared a crumb. Both claim to have learned something.
There are footprints on the beach. There is no one on the beach. It is the whole poem.
Question: what do you call a monk with a mop? Answer: late, but everything is spotless.
Between two people there is a long table, and on it a lamp, and in the lamp the whole evening.
A seed asked the soil: what will I be? The soil said: that’s between you and the weather.
The master asked: if the elevator is both going up and going down, where is it really going? The student took the stairs.
The sky has no corners, and still, somehow, you can’t get in.
What is on the other side of the sound of rain?
A pot of tea at dusk is a small, warm country. Nobody has declared war on it.
A cat is both inside the box and outside. It’s mostly annoyed.
The wind passes through the bamboo and the bamboo makes a sound. Is it the wind’s, or the bamboo’s?
The page is the only place in the world where a dot is a sentence.
The master said: a little hunger is good. The student said: what about a lot? The master said: that’s called lunchtime.
Everyone has a mountain. Some are very small and live in a pocket.
A frog sat on a lily pad and thought nothing. The lily pad was not asked, but was very supportive.
When you reach for the cup, who is reaching, and who is it for?
Which is stranger: that the cat looks at the wall, or that the wall looks back?
The master looked at the blank page for a very long time. Then he sneezed. The student wrote it down as scripture.
Autumn is the tree remembering how to be a skeleton.
An honest mirror is the thing most likely to start a fight.
What is more silent: a very large room that is empty, or a very small room that is full?
The blank day, like the blank page, has a lot to say and no way to say it. Be the way.
In another world there is a version of you who has never lost a pen. It is not clear that he is happier.
When the bell stops, where is the ringing?
The sea is always arriving. It has never once got there.
Some days the line wobbles. Be kind to the line. It’s just got a lot on its mind.
Why does the left sock always know where the right one is, and say nothing?
A leaf, a pebble, a cup. The master arranges them. The student says: it’s just three things. The master says: three things! At last.
At the end of the day, set the day down. It will be there, like a small animal, in the morning.
An eraser is just a pencil that regrets. Be glad we do not have one.
There is a path in the woods that only appears when you are not looking for it.
The day is over. It goes the way a guest goes: quietly, taking nothing, leaving the room a little warmer.
A monk once built a ladder to the moon. He ran out of ladder, but not enthusiasm.
A candle is not less bright for lighting another.
A bowl of rice. A bowl of nothing. The master points at the bowl. A student sneezes. Dinner is served.
On the road to the village there is a stone. It has been there longer than the road.
A spiral is a straight line that took the scenic route.
The master said: sit with your feelings. The student said: they’re a bit loud. The master said: they brought their friends.
An empty day is a day with all the doors open. Be careful of the draught.
A pair of old shoes by the door has seen more of the world than you, and still wants to be taken for a walk.
Winter comes slowly, then all at once, like a sentence.
A thought is a line that hasn’t decided yet.
The elephant asked the mouse: how do you hide in a room? The mouse said: I don’t hide. I’m just tiny.
A tree is a very slow explosion. Be kind to it.
Pour water into a lake. Which water do you get back?
At the edge of the village there was a tree that grew sideways. Everyone assumed it was the wind. It was a tree with ideas.
A monk asked the master: is it better to be a mountain or a very good hill? The master said: depends who’s hiking.
A spoon of rice, a cup of tea, a very small amount of light. A day.
The reflection of the moon in the bowl is not smaller than the moon.
“Master, I have achieved emptiness.” “Wonderful. Could you take out the bins?”
Look at what is in front of you. It has been waiting a very long time to be seen.
Walk slowly enough to meet the road.
A ladder leaning on nothing is a very optimistic ladder.
When you are very still, you can hear your own heart tell a very old story. It is the same one, every time.
To get to the other side, the chicken must first understand which side it’s on.
A wave goes out to sea and does not return. The sea does not mind. It was the sea all along.
What sock? Which foot?
A page may be turned, but the day is the one that turns it.
It is a very ordinary day. Be careful. This is how the whole of life is made.
If you listen very closely, the morning has a sound. It is not the birds.
A goldfish asked the master what water is. The master said: no idea, I’m just here for the snacks.
A footprint in the snow is both an arrival and a departure.
You are walking into the day as one walks into a very large, very quiet room.
When it is very quiet, the hour speaks. It says: now.
Somewhere, a lighthouse is telling a joke to the fog. The fog is a tough crowd.
The wind has no hands, and yet the curtains are everywhere.
A monk asked where the sound of the temple bell goes. The master said: probably to a nicer neighbourhood.
Someone is walking home. Someone has been walking home for a very long time.
The dew on the grass is gone by noon. It was never here to stay. It was here to be morning.
The master held up a spoon. “This is a spoon.” The student waited. “That’s it. It’s a good spoon.”
Two travellers meet at the crossroads. Neither knows the way. Both are very polite.
The little boat on the pond said: I know where I am going. The pond said: you’re going round.
A day with a hole in it is still a day. You can carry things in it.
Each of us is a drawing the day is still working on.
The master said the way is straight. Then he turned left, because of a dog.
In every cup of water is the whole of the river, but only for a moment.
Somewhere, a very patient mountain is waiting for its name.
A leaf drifts down a stream. Is it going somewhere, or is the stream bringing the somewhere to it?
Draw a house. Now draw a very small person who lives in it. Now try telling them they have to pay rent.
""".trimIndent().lines().filter { it.isNotBlank() }

    fun forDay(day: LocalDate): String =
        all[Math.floorMod(day.toEpochDay() + OFFSET, all.size.toLong()).toInt()]
            .replace("{day}", day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
}
