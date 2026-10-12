package com.dessalines.thumbkey.dictation.models

data class WhisperModel(
    val id: String,
    val title: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
    val revision: String,
) {
    val url: String = "https://huggingface.co/ggerganov/whisper.cpp/resolve/$revision/$fileName"
}

object WhisperModelCatalog {
    private const val REVISION = "5359861c739e955e79d9a303bcbc70fb988958b1"
    val models =
        listOf(
            WhisperModel(
                "tiny",
                "Tiny",
                "ggml-tiny.bin",
                77_691_713,
                "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
                REVISION,
            ),
            WhisperModel(
                "base",
                "Base (recommended)",
                "ggml-base.bin",
                147_951_465,
                "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
                REVISION,
            ),
            WhisperModel(
                "small",
                "Small",
                "ggml-small.bin",
                487_601_967,
                "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
                REVISION,
            ),
            WhisperModel(
                "turbo-q5",
                "Turbo Q5 (experimental)",
                "ggml-large-v3-turbo-q5_0.bin",
                574_041_195,
                "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
                REVISION,
            ),
        )
    val recommended = models.single { it.id == "base" }

    fun find(id: String) = models.firstOrNull { it.id == id }
}
