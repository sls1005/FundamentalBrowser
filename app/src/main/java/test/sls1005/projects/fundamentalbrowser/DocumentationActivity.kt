package test.sls1005.projects.fundamentalbrowser

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.google.android.material.appbar.MaterialToolbar

class DocumentationActivity: ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentation)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
    }
}