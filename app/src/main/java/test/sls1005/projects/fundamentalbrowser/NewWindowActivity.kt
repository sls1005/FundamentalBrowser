package test.sls1005.projects.fundamentalbrowser

class NewWindowActivity : MainActivity() {
    override fun afterInitialization() {
        if (urlToLoad.isNotEmpty()) {
            load(urlToLoad)
            urlToLoad = ""
        }
    }
}
