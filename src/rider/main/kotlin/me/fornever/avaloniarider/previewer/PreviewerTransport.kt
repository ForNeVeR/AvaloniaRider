package me.fornever.avaloniarider.previewer

sealed class PreviewerTransport {
    abstract fun getOptions(): List<String>
}
data class PreviewerBsonTransport(val bsonPort: Int) : PreviewerTransport() {
    override fun getOptions() = listOf(
        "--transport", "tcp-bson://127.0.0.1:$bsonPort/"
    )
}
