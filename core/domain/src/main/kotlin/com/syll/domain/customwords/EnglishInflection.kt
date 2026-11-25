package com.syll.domain.customwords

/**
 * Whether a word in an example is a form of the lemma being added ("ran" of "run", "studies" of "study"). iOS asks
 * `NLTagger` for each word's lemma; Android has no on-device lemmatizer, so this covers regular English inflection
 * (plural / third person, past, -ing) and the common irregular verbs, comparatives and plurals. Agent nouns and
 * regular comparatives ("worker", "faster") are left out: they would accept a different word. A miss only means
 * the learner places the word by typing it in its base form; it never accepts an unrelated word.
 */
object EnglishInflection {
    fun isForm(word: String, lemma: String): Boolean {
        val w = word.lowercase().replace('’', '\'')
        val base = lemma.trim().lowercase()
        if (w.isEmpty() || base.isEmpty() || base.contains(' ')) return w == base
        if (w == base) return true
        irregular[base]?.let { if (w in it) return true }
        return w in regularForms(base)
    }

    private fun regularForms(base: String): Set<String> {
        val forms = mutableSetOf<String>()
        val last = base.last()
        val beforeLast = base.getOrNull(base.length - 2)
        val endsConsonantY = last == 'y' && beforeLast != null && beforeLast !in VOWELS
        // Plural, third person.
        forms += when {
            base.endsWith("s") || base.endsWith("x") || base.endsWith("z") || base.endsWith("ch") || base.endsWith("sh") -> base + "es"
            endsConsonantY -> base.dropLast(1) + "ies"
            base.endsWith("fe") -> base.dropLast(2) + "ves"
            last == 'o' && beforeLast != null && beforeLast !in VOWELS -> base + "es"
            else -> base + "s"
        }
        forms += base + "s"
        forms += base + "'s"
        if (base.endsWith("f")) forms += base.dropLast(1) + "ves"
        // Past, participle, -ing, comparatives.
        val doubled = if (doublesFinalConsonant(base)) base + last else base
        when {
            last == 'e' -> {
                forms += base + "d"
                forms += base.dropLast(1) + "ing"
                if (base.endsWith("ie")) forms += base.dropLast(2) + "ying"
            }
            endsConsonantY -> {
                forms += base.dropLast(1) + "ied"
                forms += base + "ing"
            }
            else -> {
                forms += doubled + "ed"
                forms += doubled + "ing"
                forms += base + "ed"
                forms += base + "ing"
            }
        }
        if (last == 'c') {
            forms += base + "ked"
            forms += base + "king"
        }
        return forms
    }

    /** "stop" → "stopped": one vowel before one final consonant (not w, x, y). Short words only, to stay safe. */
    private fun doublesFinalConsonant(base: String): Boolean {
        if (base.length < 3 || base.length > 6) return false
        val c = base[base.length - 1]
        val v = base[base.length - 2]
        val before = base[base.length - 3]
        return c !in VOWELS && c !in "wxy" && v in VOWELS && before !in VOWELS
    }

    private const val VOWELS = "aeiou"

    private val irregular: Map<String, Set<String>> = listOf(
        "be" to "am is are was were been being",
        "have" to "has had having",
        "do" to "does did done doing",
        "go" to "goes went gone going",
        "run" to "runs ran running",
        "come" to "comes came coming",
        "become" to "becomes became becoming",
        "begin" to "begins began begun beginning",
        "bring" to "brings brought bringing",
        "buy" to "buys bought buying",
        "build" to "builds built building",
        "catch" to "catches caught catching",
        "choose" to "chooses chose chosen choosing",
        "deal" to "deals dealt dealing",
        "draw" to "draws drew drawn drawing",
        "drink" to "drinks drank drunk drinking",
        "drive" to "drives drove driven driving",
        "eat" to "eats ate eaten eating",
        "fall" to "falls fell fallen falling",
        "feel" to "feels felt feeling",
        "fight" to "fights fought fighting",
        "find" to "finds found finding",
        "fly" to "flies flew flown flying",
        "forget" to "forgets forgot forgotten forgetting",
        "forgive" to "forgives forgave forgiven forgiving",
        "freeze" to "freezes froze frozen freezing",
        "get" to "gets got gotten getting",
        "give" to "gives gave given giving",
        "grow" to "grows grew grown growing",
        "hang" to "hangs hung hanging",
        "hear" to "hears heard hearing",
        "hide" to "hides hid hidden hiding",
        "hold" to "holds held holding",
        "keep" to "keeps kept keeping",
        "know" to "knows knew known knowing",
        "lay" to "lays laid laying",
        "lead" to "leads led leading",
        "leave" to "leaves left leaving",
        "lend" to "lends lent lending",
        "lie" to "lies lay lain lying",
        "lose" to "loses lost losing",
        "make" to "makes made making",
        "mean" to "means meant meaning",
        "meet" to "meets met meeting",
        "pay" to "pays paid paying",
        "ride" to "rides rode ridden riding",
        "ring" to "rings rang rung ringing",
        "rise" to "rises rose risen rising",
        "say" to "says said saying",
        "see" to "sees saw seen seeing",
        "seek" to "seeks sought seeking",
        "sell" to "sells sold selling",
        "send" to "sends sent sending",
        "shake" to "shakes shook shaken shaking",
        "shine" to "shines shone shining",
        "shoot" to "shoots shot shooting",
        "show" to "shows showed shown showing",
        "sing" to "sings sang sung singing",
        "sink" to "sinks sank sunk sinking",
        "sit" to "sits sat sitting",
        "sleep" to "sleeps slept sleeping",
        "speak" to "speaks spoke spoken speaking",
        "spend" to "spends spent spending",
        "stand" to "stands stood standing",
        "steal" to "steals stole stolen stealing",
        "stick" to "sticks stuck sticking",
        "strike" to "strikes struck striking",
        "swim" to "swims swam swum swimming",
        "take" to "takes took taken taking",
        "teach" to "teaches taught teaching",
        "tear" to "tears tore torn tearing",
        "tell" to "tells told telling",
        "think" to "thinks thought thinking",
        "throw" to "throws threw thrown throwing",
        "understand" to "understands understood understanding",
        "wake" to "wakes woke woken waking",
        "wear" to "wears wore worn wearing",
        "win" to "wins won winning",
        "write" to "writes wrote written writing",
        "arise" to "arises arose arisen arising",
        "bear" to "bears bore borne born bearing",
        "beat" to "beats beat beaten beating",
        "bend" to "bends bent bending",
        "bind" to "binds bound binding",
        "bite" to "bites bit bitten biting",
        "blow" to "blows blew blown blowing",
        "break" to "breaks broke broken breaking",
        "breed" to "breeds bred breeding",
        "burn" to "burns burnt burned burning",
        "cling" to "clings clung clinging",
        "creep" to "creeps crept creeping",
        "dig" to "digs dug digging",
        "dream" to "dreams dreamt dreamed dreaming",
        "feed" to "feeds fed feeding",
        "flee" to "flees fled fleeing",
        "forbid" to "forbids forbade forbidden forbidding",
        "foresee" to "foresees foresaw foreseen foreseeing",
        "lean" to "leans leant leaned leaning",
        "leap" to "leaps leapt leaped leaping",
        "learn" to "learns learnt learned learning",
        "light" to "lights lit lighted lighting",
        "mislead" to "misleads misled misleading",
        "overcome" to "overcomes overcame overcoming",
        "overtake" to "overtakes overtook overtaken overtaking",
        "slide" to "slides slid sliding",
        "spin" to "spins spun spinning",
        "spread" to "spreads spreading",
        "spring" to "springs sprang sprung springing",
        "sting" to "stings stung stinging",
        "stride" to "strides strode stridden striding",
        "strive" to "strives strove striven striving",
        "swear" to "swears swore sworn swearing",
        "sweep" to "sweeps swept sweeping",
        "swing" to "swings swung swinging",
        "undergo" to "undergoes underwent undergone undergoing",
        "undertake" to "undertakes undertook undertaken undertaking",
        "weep" to "weeps wept weeping",
        "withdraw" to "withdraws withdrew withdrawn withdrawing",
        "good" to "better best",
        "bad" to "worse worst",
        "far" to "farther further farthest furthest",
        "child" to "children",
        "man" to "men",
        "woman" to "women",
        "person" to "people",
        "foot" to "feet",
        "tooth" to "teeth",
        "mouse" to "mice",
        "goose" to "geese",
        "analysis" to "analyses",
        "crisis" to "crises",
        "criterion" to "criteria",
        "phenomenon" to "phenomena",
        "datum" to "data",
        "medium" to "media",
    ).associate { (base, forms) -> base to forms.split(' ').toSet() }
}
