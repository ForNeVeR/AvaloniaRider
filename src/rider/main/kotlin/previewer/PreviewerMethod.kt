package me.fornever.avaloniarider.previewer

sealed class PreviewerMethod {
    abstract fun getOptions(): List<String>
}
object AvaloniaRemoteMethod : PreviewerMethod() {
    override fun getOptions() = listOf(
        "--method", "avalonia-remote"
    )
}
object HtmlMethod : PreviewerMethod() {
    override fun getOptions() = listOf(
        "--method", "html",
        "--html-url", "http://127.0.0.1:5000"
    )
}
