package com.vurka.emogy.data

import com.vurka.emogy.model.EmojiEntry
import kotlin.random.Random

object EmojiCatalog {

    private fun group(category: String, vararg raw: String): List<EmojiEntry> =
        raw.map { item ->
            val emoji = item.substringBefore(' ')
            EmojiEntry(id = emoji, emoji = emoji, category = category, name = item.substringAfter(' '))
        }

    private val allItems: List<EmojiEntry> = (
        group("Animals",
            "🐶 Dog", "🐱 Cat", "🦊 Fox", "🐼 Panda", "🐨 Koala", "🦁 Lion", "🐯 Tiger", "🐰 Rabbit",
            "🐸 Frog", "🦉 Owl", "🦄 Unicorn", "🐧 Penguin", "🐝 Bee", "🦋 Butterfly", "🐙 Octopus",
            "🐳 Whale", "🐵 Monkey", "🐮 Cow", "🐷 Pig", "🐻 Bear", "🐔 Chicken", "🐢 Turtle",
            "🐬 Dolphin", "🦈 Shark", "🐞 Ladybug", "🦀 Crab", "🐊 Crocodile", "🦒 Giraffe",
            "🦓 Zebra", "🐘 Elephant", "🦔 Hedgehog", "🐹 Hamster") +
        group("Wild",
            "🐛 Caterpillar", "🐜 Ant", "🦗 Cricket", "🕷️ Spider", "🦂 Scorpion", "🐌 Snail", "🦆 Duck",
            "🦅 Eagle", "🦜 Parrot", "🦢 Swan", "🦩 Flamingo", "🐦 Bird", "🦇 Bat", "🐺 Wolf", "🐗 Boar",
            "🐴 Horse", "🐑 Sheep", "🐐 Goat", "🦌 Deer", "🦏 Rhino", "🐫 Camel", "🦘 Kangaroo") +
        group("Food",
            "🍕 Pizza", "🍔 Burger", "🍟 Fries", "🍣 Sushi", "🍩 Donut", "🍪 Cookie", "🍦 Ice Cream",
            "🍓 Strawberry", "🍉 Watermelon", "🥑 Avocado", "🍿 Popcorn", "🧁 Cupcake", "🍎 Apple",
            "🍌 Banana", "🍇 Grapes", "🍒 Cherries", "🍑 Peach", "🍍 Pineapple", "🥕 Carrot", "🌽 Corn",
            "🥐 Croissant", "🧀 Cheese", "🌮 Taco", "🍜 Noodles", "🍰 Cake", "🍫 Chocolate", "🍭 Lollipop",
            "🥞 Pancakes") +
        group("Nature",
            "✨ Sparkles", "⭐ Star", "🌈 Rainbow", "☀️ Sun", "🌙 Moon", "🔥 Fire", "🌸 Blossom",
            "🌵 Cactus", "🌴 Palm", "❄️ Snowflake", "🌻 Sunflower", "🌹 Rose", "🍀 Clover", "🍁 Maple Leaf",
            "🌲 Pine", "🌊 Wave", "⚡ Lightning", "☁️ Cloud", "🍄 Mushroom", "🌺 Hibiscus", "💧 Droplet",
            "🌷 Tulip") +
        group("Faces",
            "😀 Grin", "😍 Heart Eyes", "😎 Cool", "🥳 Party Face", "🤔 Thinking", "😇 Angel", "👻 Ghost",
            "👽 Alien", "🤖 Robot", "🤡 Clown", "💀 Skull", "😉 Wink", "😂 Joy", "🥰 Love", "😴 Sleepy",
            "🤩 Star-Struck", "😱 Scream", "🤠 Cowboy", "🤓 Nerd", "😜 Silly", "🥶 Cold", "🥵 Hot",
            "😈 Devil", "🤗 Hug") +
        group("Sports",
            "⚽ Soccer", "🏀 Basketball", "🏆 Trophy", "🥇 Gold Medal", "🎮 Video Game", "🎸 Guitar",
            "🎨 Palette", "🚀 Rocket", "🏈 Football", "🎾 Tennis", "🎯 Bullseye", "🎲 Dice", "⚾ Baseball",
            "🏐 Volleyball", "🏉 Rugby", "🎱 Pool", "🏓 Ping Pong", "🥊 Boxing", "⛳ Golf", "🎳 Bowling",
            "🏹 Archery", "🛹 Skateboard", "🎻 Violin", "🥁 Drum") +
        group("Symbols",
            "💎 Gem", "👑 Crown", "🔑 Key", "🔒 Lock", "🎁 Gift", "🎈 Balloon", "🔔 Bell", "💡 Bulb",
            "🔋 Battery", "🧭 Compass", "⏳ Hourglass", "⚓ Anchor", "💰 Money Bag", "🔮 Crystal Ball",
            "🧲 Magnet", "🔭 Telescope", "🔬 Microscope", "📷 Camera", "⌚ Watch", "📱 Phone") +
        group("Space",
            "🌌 Galaxy", "🌠 Shooting Star", "🛸 UFO", "🛰️ Satellite", "🌑 New Moon", "🌓 Half Moon",
            "🌕 Full Moon", "🌟 Glowing Star", "💫 Dizzy Star", "☄️ Comet", "🌞 Sun Face", "🌝 Moon Face",
            "🌛 Moon Left", "🌜 Moon Right", "🌚 Dark Moon", "🌎 Earth", "🌏 Asia Earth", "🌐 Globe",
            "🌘 Waning Moon", "🌗 Last Quarter") +
        group("Travel",
            "🚗 Car", "🚕 Taxi", "🚌 Bus", "🚓 Police Car", "🚑 Ambulance", "🚒 Fire Truck", "🚜 Tractor",
            "🚲 Bicycle", "🛵 Scooter", "🚂 Train", "✈️ Plane", "🚁 Helicopter", "⛵ Sailboat", "🚢 Ship",
            "🚤 Speedboat", "🏰 Castle", "🗼 Tower", "🗽 Statue", "🎡 Ferris Wheel", "🎢 Roller Coaster",
            "⛺ Tent", "🏖️ Beach") +
        group("Fantasy",
            "🐉 Dragon", "🐲 Dragon Face", "🧙 Wizard", "🧚 Fairy", "🧛 Vampire", "🧜 Merperson", "🧝 Elf",
            "🧞 Genie", "🧟 Zombie", "🦖 T-Rex", "🦕 Dino", "🔱 Trident", "⚔️ Swords", "🛡️ Shield",
            "🗡️ Dagger", "🧿 Evil Eye", "🎭 Masks", "📜 Scroll", "🏺 Vase", "🧪 Potion", "👹 Ogre", "👺 Goblin") +
        group("Party",
            "🎧 Headphones", "🎤 Mic", "🎹 Piano", "🎷 Sax", "🎼 Score", "🎬 Clapper", "🎠 Carousel",
            "🎉 Party Popper", "🎊 Confetti", "🎂 Birthday Cake", "🥂 Cheers", "🎀 Ribbon", "🧸 Teddy",
            "🎄 Tree", "🎃 Pumpkin", "🎆 Fireworks", "🎇 Sparkler", "🧨 Firecracker")
        ).distinctBy { it.id }

    @Volatile
    private var glyphFilter: (String) -> Boolean = { true }

    @Volatile
    private var filteredItems: List<EmojiEntry>? = null

    /**
     * Emoji the current device can actually draw. The filter is installed once at startup; it is
     * also reset by tests. If a device supports too few emoji for a level, [getPoolForCategories]
     * falls back to the raw catalogue so a board is still playable instead of crashing.
     */
    val items: List<EmojiEntry>
        get() = filteredItems ?: synchronized(this) {
            filteredItems ?: allItems.filter { entry -> glyphFilter(entry.emoji) }.also { filteredItems = it }
        }

    fun installGlyphFilter(filter: (String) -> Boolean) {
        glyphFilter = filter
        filteredItems = null
    }

    fun resetGlyphFilter() = installGlyphFilter { true }

    val animalEmojiIds: Set<String> by lazy {
        allItems.filter { it.category == "Animals" }.mapTo(HashSet()) { it.emoji }
    }

    fun getByCategory(category: String): List<EmojiEntry> = items.filter { it.category == category }

    fun getAllCategories(): List<String> = items.map { it.category }.distinct()

    /**
     * Builds a shuffled pool of at least [requiredCount] distinct emojis that
     * preferentially come from [categories]. If the chosen categories cannot
     * supply enough unique emojis, the rest are filled from the full catalog so
     * deck generation never fails.
     */
    fun getPoolForCategories(
        categories: List<String>,
        requiredCount: Int,
        random: Random
    ): List<EmojiEntry> {
        if (requiredCount <= 0) return emptyList()

        val supported = items
        val preferred = if (categories.isEmpty()) emptyList() else supported.filter { it.category in categories }
        val pool = preferred.shuffled(random).toMutableList()
        if (pool.size < requiredCount) {
            val preferredIds = preferred.mapTo(HashSet()) { it.id }
            pool.addAll(supported.filter { it.id !in preferredIds }.shuffled(random))
        }
        if (pool.size < requiredCount) {
            // Extremely old/limited font stack: a possible tofu box is better than an unplayable
            // board. Modern devices never reach this branch. Only fill the shortfall, so a tiny
            // deck never drags the whole catalogue into memory or loses its category mix.
            val poolIds = pool.mapTo(HashSet()) { it.id }
            val shortfall = requiredCount - pool.size
            pool.addAll(
                allItems.filter { it.id !in poolIds }.shuffled(random).take(shortfall)
            )
        }
        return pool
    }
}
