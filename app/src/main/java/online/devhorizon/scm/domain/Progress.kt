package online.devhorizon.scm.domain

/** Progress reporting contract shared by the indexer, pipeline and UI. */
interface Progress {
    fun message(msg: String)
    fun progress(done: Int, total: Int)
    fun isCancelled(): Boolean

    companion object {
        fun noop(): Progress = object : Progress {
            override fun message(msg: String) {}
            override fun progress(done: Int, total: Int) {}
            override fun isCancelled(): Boolean = false
        }
    }
}
