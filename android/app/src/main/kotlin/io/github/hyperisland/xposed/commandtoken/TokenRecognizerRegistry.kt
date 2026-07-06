package io.github.hyperisland.xposed.commandtoken

import java.util.concurrent.ConcurrentHashMap

object TokenRecognizerRegistry {

    private val recognizers = ConcurrentHashMap<String, TokenRecognizer>()

    init {
        register(DouyinTokenRecognizer)
    }

    fun register(recognizer: TokenRecognizer) {
        recognizers[recognizer.recognizerId] = recognizer
    }

    fun unregister(id: String) {
        recognizers.remove(id)
    }

    fun all(): List<TokenRecognizer> = recognizers.values.toList()
}
