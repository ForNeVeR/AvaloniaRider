package me.fornever.avaloniarider.previewer

sealed class PreviewerMethod {
    abstract fun getOptions(freePortGenerator: () -> Int): List<String>
}
object AvaloniaRemoteMethod : PreviewerMethod() {
    override fun getOptions(freePortGenerator: () -> Int) = listOf(
        "--method", "avalonia-remote"
    )
}
object HtmlMethod : PreviewerMethod() {
    override fun getOptions(freePortGenerator: () -> Int) = listOf(
        "--method", "html",
        "--html-url", "http://127.0.0.1:${freePortGenerator()}"
    )
}
